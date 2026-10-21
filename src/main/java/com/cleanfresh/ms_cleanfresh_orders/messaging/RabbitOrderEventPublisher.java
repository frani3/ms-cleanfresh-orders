package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publica los comandos de cada orden en RabbitMQ (app.rabbit.enabled=true), con el envelope común,
 * persistentes y con confirmación del broker.
 *
 * <ul>
 *   <li>Al crear una orden: {@code kitchen.ticket} (ticket de taller) y {@code email.send} (aviso al
 *       Operador de la sucursal).</li>
 *   <li>Al pasar a DESPACHADO: {@code email.send} (aviso al Cliente) e {@code invoice.gen} (boleta).</li>
 *   <li>Al pasar a ACEPTADO, EN_PREPARACION, ENTREGADO o CANCELADO (Spec 035): {@code email.send} (aviso al
 *       Cliente del nuevo estado, que va en {@code payload.estado}).</li>
 * </ul>
 *
 * Las órdenes de Servicio exprés van por {@code cmd.topic} con la variante de la clave
 * ({@code email.send.high}, {@code kitchen.ticket.thermal}, {@code invoice.gen.pdf}); las demás, por
 * {@code cmd.direct} con la clave base.
 */
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventPublisher implements OrderEventPublisher {

    static final String EXCHANGE_DIRECT = "cmd.direct";
    static final String EXCHANGE_TOPIC = "cmd.topic";
    static final long CONFIRMACION_SEGUNDOS = 5;

    // Variante de cada clave base cuando la orden es exprés (se enruta por cmd.topic).
    private static final Map<String, String> VARIANTE_EXPRES = Map.of(
            "email.send", "email.send.high",
            "kitchen.ticket", "kitchen.ticket.thermal",
            "invoice.gen", "invoice.gen.pdf");

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventPublisher.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RabbitTemplate rabbit;

    public RabbitOrderEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publishCreada(OrdenCreadaEvent event) {
        publicar(event.orden(), "ORDEN_CREADA", "kitchen.ticket", "email.send");
    }

    @Override
    public void publishLista(OrdenListaEvent event) {
        publicar(event.orden(), "ORDEN_LISTA", "email.send", "invoice.gen");
    }

    @Override
    public void publishEstado(OrdenEstadoEvent event) {
        publicar(event.orden(), "ORDEN_ESTADO", "email.send");
    }

    // Se intentan todos los comandos aunque uno falle (son independientes) y al final se avisa del error.
    private void publicar(OrderResponse orden, String type, String... clavesBase) {
        String traceId = UUID.randomUUID().toString();
        RuntimeException primerError = null;
        for (String claveBase : clavesBase) {
            try {
                enviar(orden, type, traceId, claveBase);
            } catch (RuntimeException e) {
                log.error("No se pudo publicar {} ({}) de {}", claveBase, type, orden.numeroOrden(), e);
                if (primerError == null) {
                    primerError = e;
                }
            }
        }
        if (primerError != null) {
            throw primerError;
        }
    }

    private void enviar(OrderResponse orden, String type, String traceId, String claveBase) {
        boolean expres = esExpres(orden);
        String exchange = expres ? EXCHANGE_TOPIC : EXCHANGE_DIRECT;
        String clave = expres ? VARIANTE_EXPRES.get(claveBase) : claveBase;

        String eventId = UUID.randomUUID().toString();
        Envelope envelope = new Envelope(type, eventId, Instant.now().toString(), traceId, orden.numeroOrden(),
                new Envelope.OrdenPayload(orden.numeroOrden(), orden.cliente(), orden.clienteNombre(),
                        orden.servicio(), orden.sucursal(), orden.total(), orden.fecha(), orden.estado()));
        Message message = MessageBuilder
                .withBody(MAPPER.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(eventId)
                .setCorrelationId(orden.numeroOrden())
                .setHeader("traceId", traceId)
                .build();

        CorrelationData confirmacion = new CorrelationData(eventId);
        rabbit.send(exchange, clave, message, confirmacion);
        esperarConfirmacion(confirmacion, exchange, clave);
        log.info("Comando {} publicado en {} (eventId {}, orden {})", clave, exchange, eventId, orden.numeroOrden());
    }

    // El broker debe confirmar (ack) y el mensaje no debe haber vuelto por no tener binding.
    private void esperarConfirmacion(CorrelationData confirmacion, String exchange, String clave) {
        try {
            CorrelationData.Confirm resultado = confirmacion.getFuture().get(CONFIRMACION_SEGUNDOS, TimeUnit.SECONDS);
            if (!resultado.ack()) {
                throw new IllegalStateException("RabbitMQ no confirmó " + clave + " en " + exchange + ": " + resultado.reason());
            }
            if (confirmacion.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ no pudo enrutar " + clave + " en " + exchange + " (sin binding)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando la confirmación de " + clave, e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Sin confirmación de RabbitMQ para " + clave + " en " + exchange, e);
        }
    }

    // El servicio exprés se identifica por su nombre (sin importar tildes ni mayúsculas).
    static boolean esExpres(OrderResponse orden) {
        return orden.servicio() != null && orden.servicio().toLowerCase().contains("expr");
    }
}
