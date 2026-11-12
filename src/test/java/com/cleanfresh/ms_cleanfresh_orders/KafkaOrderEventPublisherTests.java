package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.config.AppKafkaProperties;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.Actor;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.kafka.AuditEntryMessage;
import com.cleanfresh.ms_cleanfresh_orders.messaging.kafka.KafkaOrderEventPublisher;
import com.cleanfresh.ms_cleanfresh_orders.messaging.kafka.OrderEventMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 044: lo que {@code orders} publica en Kafka (orders.events y audit.timeline). */
@SuppressWarnings("unchecked")
class KafkaOrderEventPublisherTests {

    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final AppKafkaProperties props = new AppKafkaProperties(true, null);
    private final List<Runnable> pendientes = new ArrayList<>();
    private KafkaOrderEventPublisher publisher;

    private static final Actor OPERADOR = new Actor("op-1234", "190.5.59.49");

    @BeforeEach
    void preparar() {
        // El envío real corre en otro hilo; acá se ejecuta de inmediato, salvo en la prueba que comprueba que no bloquea.
        publisher = new KafkaOrderEventPublisher(kafka, props, Runnable::run);
        SendResult<String, Object> resultado = mock(SendResult.class);
        when(resultado.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(resultado));
    }

    private static OrderResponse orden(String estado) {
        return new OrderResponse(7L, "ORD-0007", "64888468-uuid", "Planchado", estado, "2026-10-15", 9500.0,
                "Providencia", "cliente@cleanfresh.com", "2026-10-15T14:02:03.123456Z");
    }

    private List<ProducerRecord<String, Object>> enviados(int esperados) {
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(esperados)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String cabecera(ProducerRecord<String, Object> registro, String nombre) {
        Header h = registro.headers().lastHeader(nombre);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    @Test
    void alCrearUnaOrdenPublicaOrderCreatedYSuRegistroDeAuditoria() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("CREADO"), OPERADOR));

        List<ProducerRecord<String, Object>> registros = enviados(2);
        ProducerRecord<String, Object> eventoRegistro = registros.get(0);
        assertEquals("orders.events", eventoRegistro.topic());
        assertEquals("ORD-0007", eventoRegistro.key());
        OrderEventMessage evento = (OrderEventMessage) eventoRegistro.value();
        assertEquals("OrderCreated", evento.type());
        assertEquals("ORD-0007", evento.correlationId());
        assertNotNull(evento.eventId());
        assertNotNull(evento.traceId());
        assertEquals("ORD-0007", evento.payload().numeroOrden());
        assertEquals("CREADO", evento.payload().estado());
        assertEquals("Planchado", evento.payload().servicio());
        assertEquals("Providencia", evento.payload().sucursal());
        assertEquals(9500.0, evento.payload().total());
        assertEquals("2026-10-15T14:02:03.123456Z", evento.payload().fechaHora());
        assertEquals("op-1234", evento.payload().actor());

        ProducerRecord<String, Object> auditoriaRegistro = registros.get(1);
        AuditEntryMessage auditoria = (AuditEntryMessage) auditoriaRegistro.value();
        assertEquals("audit.timeline", auditoriaRegistro.topic());
        assertEquals("orden:ORD-0007:" + auditoria.eventId(), auditoriaRegistro.key());
        assertEquals("op-1234", auditoria.actor());
        assertEquals("OrderCreated", auditoria.accion());
        assertEquals("orden", auditoria.entidad());
        assertEquals("ORD-0007", auditoria.entidadId());
        assertEquals("190.5.59.49", auditoria.origen());
        assertEquals("ms-cleanfresh-orders", auditoria.fuente());
        assertTrue(auditoria.detalle().contains("Planchado"));
        // el evento de negocio y su auditoría comparten la traza
        assertEquals(evento.traceId(), auditoria.traceId());
        assertNotEquals(evento.eventId(), auditoria.eventId());
    }

    @Test
    void cadaMensajeLlevaSusCabecerasParaVerlasEnKafkaUi() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("CREADO"), OPERADOR));

        ProducerRecord<String, Object> registro = enviados(2).get(0);
        OrderEventMessage evento = (OrderEventMessage) registro.value();
        assertEquals("OrderCreated", cabecera(registro, "type"));
        assertEquals(evento.eventId(), cabecera(registro, "eventId"));
        assertEquals(evento.traceId(), cabecera(registro, "traceId"));
    }

    @Test
    void despacharPublicaOrderDispatched() {
        publisher.publishLista(new OrdenListaEvent(orden("DESPACHADO"), OPERADOR));

        OrderEventMessage evento = (OrderEventMessage) enviados(2).get(0).value();
        assertEquals("OrderDispatched", evento.type());
        assertEquals("DESPACHADO", evento.payload().estado());
    }

    @Test
    void cadaCambioDeEstadoTieneSuEventoDelCaso() {
        for (String[] par : new String[][]{{"ACEPTADO", "OrderAccepted"}, {"EN_PREPARACION", "OrderPreparing"},
                {"ENTREGADO", "OrderDelivered"}, {"CANCELADO", "OrderCancelled"}}) {
            org.mockito.Mockito.clearInvocations(kafka);

            publisher.publishEstado(new OrdenEstadoEvent(orden(par[0]), OPERADOR));

            OrderEventMessage evento = (OrderEventMessage) enviados(2).get(0).value();
            assertEquals(par[1], evento.type(), par[0]);
            assertEquals(par[0], evento.payload().estado());
        }
    }

    @Test
    void unEstadoSinEventoNoPublicaNada() {
        publisher.publishEstado(new OrdenEstadoEvent(orden("INVENTADO"), OPERADOR));

        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    void sinActorElRegistroQuedaComoDesconocido() {
        publisher.publishCreada(new OrdenCreadaEvent(orden("CREADO")));

        AuditEntryMessage auditoria = (AuditEntryMessage) enviados(2).get(1).value();
        assertEquals("desconocido", auditoria.actor());
        assertEquals(null, auditoria.origen());
    }

    @Test
    void losTopicosSalenDeLaConfiguracion() {
        AppKafkaProperties otros = new AppKafkaProperties(true, new AppKafkaProperties.Topics("pedidos.eventos", "auditoria.linea"));
        KafkaOrderEventPublisher conOtros = new KafkaOrderEventPublisher(kafka, otros, Runnable::run);

        conOtros.publishCreada(new OrdenCreadaEvent(orden("CREADO"), OPERADOR));

        List<ProducerRecord<String, Object>> registros = enviados(2);
        assertEquals("pedidos.eventos", registros.get(0).topic());
        assertEquals("auditoria.linea", registros.get(1).topic());
    }

    @Test
    void publicarNoBloqueaAQuienLlama() {
        // el ejecutor solo guarda la tarea: el envío no ocurre en el hilo de quien publica
        KafkaOrderEventPublisher asincrono = new KafkaOrderEventPublisher(kafka, props, pendientes::add);

        asincrono.publishCreada(new OrdenCreadaEvent(orden("CREADO"), OPERADOR));

        verify(kafka, never()).send(any(ProducerRecord.class));
        assertEquals(1, pendientes.size());
        pendientes.forEach(Runnable::run);
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void siKafkaFallaNoSePropagaYSeIntentaElSiguienteMensaje() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenThrow(new IllegalStateException("sin brokers"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        assertDoesNotThrow(() -> publisher.publishCreada(new OrdenCreadaEvent(orden("CREADO"), OPERADOR)));

        // el primer envío lanzó y el segundo falló de forma asíncrona: ambos se intentaron
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }
}
