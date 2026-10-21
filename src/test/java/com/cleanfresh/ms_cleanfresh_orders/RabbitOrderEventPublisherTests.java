package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.RabbitOrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.ReturnedMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Spec 033: qué comandos publica orders, por qué exchange y con qué envelope. Se usa un RabbitTemplate
 * simulado que confirma (ack) cada publicación, como haría el broker.
 */
class RabbitOrderEventPublisherTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final RabbitOrderEventPublisher publisher = new RabbitOrderEventPublisher(rabbit);

    private final List<String> exchanges = new ArrayList<>();
    private final List<String> claves = new ArrayList<>();
    private final List<Message> mensajes = new ArrayList<>();

    private static OrderResponse orden(String servicio) {
        return new OrderResponse(7L, "ORD-0007", "64888468-uuid", servicio, "CREADO", "2026-10-15", 15000.0,
                "Providencia", "cliente@cleanfresh.com");
    }

    private static OrderResponse ordenEnEstado(String servicio, String estado) {
        return new OrderResponse(7L, "ORD-0007", "64888468-uuid", servicio, estado, "2026-10-15", 15000.0,
                "Providencia", "cliente@cleanfresh.com");
    }

    private void brokerConfirma(boolean ack, boolean sinBinding) {
        doAnswer(inv -> {
            exchanges.add(inv.getArgument(0));
            claves.add(inv.getArgument(1));
            mensajes.add(inv.getArgument(2));
            CorrelationData cd = inv.getArgument(3);
            if (sinBinding) {
                cd.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", inv.getArgument(0), inv.getArgument(1)));
            }
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack simulado"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @BeforeEach
    void confirmaPorDefecto() {
        brokerConfirma(true, false);
    }

    private JsonNode cuerpo(int i) throws Exception {
        return MAPPER.readTree(new String(mensajes.get(i).getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void alCrearUnaOrdenPublicaTicketDeTallerYAvisoAlOperadorPorCmdDirect() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado")));

        assertEquals(List.of("kitchen.ticket", "email.send"), claves);
        assertEquals(List.of("cmd.direct", "cmd.direct"), exchanges);
    }

    @Test
    void alDespacharPublicaAvisoAlClienteYBoletaPorCmdDirect() {
        publisher.publishLista(new OrdenListaEvent(orden("Planchado")));

        assertEquals(List.of("email.send", "invoice.gen"), claves);
        assertEquals(List.of("cmd.direct", "cmd.direct"), exchanges);
    }

    @Test
    void unCambioDeEstadoPublicaSoloElAvisoAlClientePorCmdDirect() throws Exception {
        publisher.publishEstado(new OrdenEstadoEvent(ordenEnEstado("Planchado", "EN_PREPARACION")));

        assertEquals(List.of("email.send"), claves);
        assertEquals(List.of("cmd.direct"), exchanges);
        JsonNode json = cuerpo(0);
        assertEquals("ORDEN_ESTADO", json.get("type").asString());
        assertEquals("EN_PREPARACION", json.get("payload").get("estado").asString());
        assertEquals("64888468-uuid", json.get("payload").get("cliente").asString());
    }

    @Test
    void elAvisoDeEstadoDeUnaOrdenExpresVaPorCmdTopicConLaVariante() {
        publisher.publishEstado(new OrdenEstadoEvent(ordenEnEstado("Servicio exprés", "ENTREGADO")));

        assertEquals(List.of("email.send.high"), claves);
        assertEquals(List.of("cmd.topic"), exchanges);
    }

    @Test
    void elPayloadSiempreLlevaElEstadoDeLaOrden() throws Exception {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado")));
        publisher.publishLista(new OrdenListaEvent(ordenEnEstado("Planchado", "DESPACHADO")));

        assertEquals("CREADO", cuerpo(0).get("payload").get("estado").asString());
        assertEquals("DESPACHADO", cuerpo(2).get("payload").get("estado").asString());
    }

    @Test
    void siFallaElAvisoDeEstadoSeLanzaElErrorParaQueSeRegistre() {
        brokerConfirma(false, false);

        assertThrows(IllegalStateException.class,
                () -> publisher.publishEstado(new OrdenEstadoEvent(ordenEnEstado("Planchado", "CANCELADO"))));
    }

    @Test
    void lasOrdenesExpresVanPorCmdTopicConLaVariante() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Servicio exprés")));
        publisher.publishLista(new OrdenListaEvent(orden("Servicio exprés")));

        assertEquals(List.of("kitchen.ticket.thermal", "email.send.high", "email.send.high", "invoice.gen.pdf"), claves);
        assertTrue(exchanges.stream().allMatch("cmd.topic"::equals));
    }

    @Test
    void elEnvelopeLlevaTodosLosCamposYElPayload() throws Exception {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado")));

        JsonNode json = cuerpo(0);
        assertEquals("ORDEN_CREADA", json.get("type").asString());
        assertEquals("ORD-0007", json.get("correlationId").asString());
        assertFalse(json.get("eventId").asString().isBlank());
        assertFalse(json.get("traceId").asString().isBlank());
        assertTrue(Instant.parse(json.get("timestamp").asString()).isBefore(Instant.now().plusSeconds(5)));
        JsonNode payload = json.get("payload");
        assertEquals("ORD-0007", payload.get("numeroOrden").asString());
        assertEquals("64888468-uuid", payload.get("cliente").asString());
        assertEquals("cliente@cleanfresh.com", payload.get("clienteNombre").asString());
        assertEquals("Planchado", payload.get("servicio").asString());
        assertEquals("Providencia", payload.get("sucursal").asString());
        assertEquals(15000.0, payload.get("total").asDouble());
    }

    @Test
    void cadaMensajeTieneSuPropioEventIdPeroComparteElTraceIdDeLaOperacion() throws Exception {
        publisher.publishLista(new OrdenListaEvent(orden("Planchado")));

        String evento0 = cuerpo(0).get("eventId").asString();
        String evento1 = cuerpo(1).get("eventId").asString();
        assertFalse(evento0.equals(evento1));
        assertEquals(cuerpo(0).get("traceId").asString(), cuerpo(1).get("traceId").asString());
        Set<String> ids = new HashSet<>(List.of(mensajes.get(0).getMessageProperties().getMessageId(),
                mensajes.get(1).getMessageProperties().getMessageId()));
        assertEquals(Set.of(evento0, evento1), ids);
    }

    @Test
    void losMensajesSonPersistentesYJson() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado")));

        var props = mensajes.get(0).getMessageProperties();
        assertEquals(MessageDeliveryMode.PERSISTENT, props.getDeliveryMode());
        assertEquals("application/json", props.getContentType());
        assertEquals("ORD-0007", props.getCorrelationId());
        assertEquals(cuerpoTraceId(0), props.getHeader("traceId"));
    }

    private String cuerpoTraceId(int i) {
        try {
            return cuerpo(i).get("traceId").asString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void siElBrokerNoConfirmaSeLanzaError() {
        mensajes.clear();
        brokerConfirma(false, false);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado"))));

        assertTrue(e.getMessage().contains("no confirmó"));
    }

    @Test
    void siElMensajeNoTieneBindingSeLanzaError() {
        brokerConfirma(true, true);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> publisher.publishLista(new OrdenListaEvent(orden("Planchado"))));

        assertTrue(e.getMessage().contains("sin binding"));
    }

    @Test
    void siUnComandoFallaIgualSeIntentaElOtro() {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            claves.add(inv.getArgument(1));
            cd.getFuture().complete(new CorrelationData.Confirm("kitchen.ticket".equals(inv.getArgument(1)) ? false : true, "nack"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThrows(IllegalStateException.class, () -> publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado"))));

        assertEquals(List.of("kitchen.ticket", "email.send"), claves);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(rabbit, times(2)).send(anyString(), captor.capture(), any(Message.class), any(CorrelationData.class));
    }

    // ---------- Spec 039: el tipo de notificación se ve en la consola de RabbitMQ ----------

    private String tipo(int i) {
        return mensajes.get(i).getMessageProperties().getType();
    }

    private Object cabecera(int i, String nombre) {
        return mensajes.get(i).getMessageProperties().getHeaders().get(nombre);
    }

    @Test
    void alCrearUnaOrdenCadaMensajeLlevaSuTipoYAQuienVa() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("Planchado")));

        // 0 = ticket de taller, 1 = aviso al Operador de la sucursal
        assertEquals("ORDEN_CREADA", tipo(0));
        assertEquals("TALLER", cabecera(0, "destinatario"));
        assertEquals("ORDEN_CREADA", tipo(1));
        assertEquals("SUCURSAL", cabecera(1, "destinatario"));
        assertEquals("CREADO", cabecera(1, "estado"));
    }

    @Test
    void alDespacharElAvisoVaAlClienteYLaBoletaTambien() {
        publisher.publishLista(new OrdenListaEvent(ordenEnEstado("Planchado", "DESPACHADO")));

        assertEquals("ORDEN_LISTA", tipo(0));
        assertEquals("CLIENTE", cabecera(0, "destinatario"));
        assertEquals("ORDEN_LISTA", tipo(1));
        assertEquals("CLIENTE", cabecera(1, "destinatario"));
        assertEquals("DESPACHADO", cabecera(1, "estado"));
    }

    @Test
    void unCambioDeEstadoEsDeTipoOrdenEstadoParaElCliente() {
        publisher.publishEstado(new OrdenEstadoEvent(ordenEnEstado("Planchado", "EN_PREPARACION")));

        assertEquals("ORDEN_ESTADO", tipo(0));
        assertEquals("CLIENTE", cabecera(0, "destinatario"));
        assertEquals("EN_PREPARACION", cabecera(0, "estado"));
    }

    @Test
    void elTipoDeLaPropiedadCoincideConElDelCuerpoYConLaCabeceraDeTraza() throws Exception {
        publisher.publishEstado(new OrdenEstadoEvent(ordenEnEstado("Servicio exprés", "ENTREGADO")));

        // lo que muestra la consola (propiedad type) es lo mismo que dice el JSON, también en la variante exprés
        assertEquals(cuerpo(0).get("type").asString(), tipo(0));
        assertEquals(cuerpo(0).get("traceId").asString(), cabecera(0, "traceId"));
        assertEquals("ORD-0007", mensajes.get(0).getMessageProperties().getCorrelationId());
    }

    @Test
    void elDestinatarioSigueLaRegla() {
        assertEquals("TALLER", RabbitOrderEventPublisher.destinatario("ORDEN_CREADA", "kitchen.ticket"));
        assertEquals("SUCURSAL", RabbitOrderEventPublisher.destinatario("ORDEN_CREADA", "email.send"));
        assertEquals("CLIENTE", RabbitOrderEventPublisher.destinatario("ORDEN_LISTA", "email.send"));
        assertEquals("CLIENTE", RabbitOrderEventPublisher.destinatario("ORDEN_ESTADO", "email.send"));
        assertEquals("CLIENTE", RabbitOrderEventPublisher.destinatario("ORDEN_LISTA", "invoice.gen"));
    }
}
