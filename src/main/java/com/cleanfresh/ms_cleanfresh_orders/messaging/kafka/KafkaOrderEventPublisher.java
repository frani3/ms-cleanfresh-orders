package com.cleanfresh.ms_cleanfresh_orders.messaging.kafka;

import com.cleanfresh.ms_cleanfresh_orders.config.AppKafkaProperties;
import com.cleanfresh.ms_cleanfresh_orders.config.KafkaConfig;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.Actor;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.OrderEventPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Publica en Kafka (Spec 044) los hechos que ya ocurrieron: cada creación o cambio de estado de una orden va a
 * {@code orders.events} (clave = número de orden, así todos los eventos de una orden caen en la misma partición y se leen en orden)
 * y su registro de auditoría a {@code audit.timeline}. Es independiente de los comandos de RabbitMQ.
 *
 * <p>El envío corre en un hilo aparte ({@link KafkaConfig#kafkaEnvio()}): si Kafka no responde, la orden igual se guarda y la
 * respuesta HTTP no se demora. Los errores se registran con el número de orden, el tipo y el tópico.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    /** Tipo de evento según el estado nuevo de la orden. CREADO no tiene evento de cambio: es OrderCreated. */
    static final Map<String, String> TIPO_POR_ESTADO = Map.of(
            "CREADO", "OrderCreated",
            "ACEPTADO", "OrderAccepted",
            "EN_PREPARACION", "OrderPreparing",
            "DESPACHADO", "OrderDispatched",
            "ENTREGADO", "OrderDelivered",
            "CANCELADO", "OrderCancelled");

    private final KafkaTemplate<String, Object> kafka;
    private final AppKafkaProperties props;
    private final Executor ejecutor;

    public KafkaOrderEventPublisher(KafkaTemplate<String, Object> kafka, AppKafkaProperties props,
                                    @org.springframework.beans.factory.annotation.Qualifier("kafkaEnvio") Executor ejecutor) {
        this.kafka = kafka;
        this.props = props;
        this.ejecutor = ejecutor;
    }

    @Override
    public void publishCreada(OrdenCreadaEvent event) {
        publicar(event.orden(), "OrderCreated", event.actor());
    }

    @Override
    public void publishLista(OrdenListaEvent event) {
        publicar(event.orden(), "OrderDispatched", event.actor());
    }

    @Override
    public void publishEstado(OrdenEstadoEvent event) {
        String tipo = TIPO_POR_ESTADO.get(event.orden().estado());
        if (tipo == null) {
            log.warn("Estado {} de {} sin evento de Kafka: no se publica", event.orden().estado(), event.orden().numeroOrden());
            return;
        }
        publicar(event.orden(), tipo, event.actor());
    }

    private void publicar(OrderResponse orden, String tipo, Actor actor) {
        Actor quien = actor == null ? Actor.DESCONOCIDO : actor;
        String traceId = UUID.randomUUID().toString();
        String ahora = Instant.now().toString();

        OrderEventMessage evento = new OrderEventMessage(tipo, UUID.randomUUID().toString(), ahora, traceId, orden.numeroOrden(),
                new OrderEventMessage.Payload(orden.numeroOrden(), orden.cliente(), orden.clienteNombre(), orden.servicio(),
                        orden.sucursal(), orden.total(), orden.estado(), orden.fecha(), orden.fechaHora(), quien.usuario()));
        AuditEntryMessage auditoria = new AuditEntryMessage(UUID.randomUUID().toString(), ahora, traceId, quien.usuario(), tipo,
                "orden", orden.numeroOrden(),
                orden.servicio() + " en " + orden.sucursal() + ", estado " + orden.estado(), quien.origen(),
                KafkaConfig.NOMBRE_SERVICIO);

        ejecutor.execute(() -> {
            enviar(props.topics().ordersEvents(), orden.numeroOrden(), evento, evento.eventId(), traceId, tipo);
            enviar(props.topics().auditTimeline(), "orden:" + orden.numeroOrden() + ":" + auditoria.eventId(), auditoria,
                    auditoria.eventId(), traceId, tipo);
        });
    }

    private void enviar(String topico, String clave, Object mensaje, String eventId, String traceId, String tipo) {
        try {
            ProducerRecord<String, Object> registro = new ProducerRecord<>(topico, null, clave, mensaje, List.of(
                    new RecordHeader("eventId", eventId.getBytes(StandardCharsets.UTF_8)),
                    new RecordHeader("traceId", traceId.getBytes(StandardCharsets.UTF_8)),
                    new RecordHeader("type", tipo.getBytes(StandardCharsets.UTF_8))));
            kafka.send(registro).whenComplete((resultado, error) -> {
                if (error != null) {
                    log.error("No se pudo publicar {} ({}) en {}; el cambio de la orden ya quedó guardado", tipo, clave, topico, error);
                } else {
                    log.info("Evento {} publicado en {} (partición {}, offset {}, clave {})", tipo, topico,
                            resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), clave);
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo publicar {} ({}) en {}; el cambio de la orden ya quedó guardado", tipo, clave, topico, e);
        }
    }
}
