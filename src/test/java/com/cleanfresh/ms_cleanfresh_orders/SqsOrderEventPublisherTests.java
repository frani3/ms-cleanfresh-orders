package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.SqsOrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El contrato con ms-cleanfresh-notificaciones: el JSON que de verdad se deja en la
 * cola de SQS (con un cliente de SQS simulado).
 */
class SqsOrderEventPublisherTests {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/cleanfresh-ordenes";

    private final SqsClient sqs = mock(SqsClient.class);
    private final SqsOrderEventPublisher publisher = new SqsOrderEventPublisher(sqs, QUEUE_URL);

    private static OrderResponse orden(String clienteNombre) {
        return new OrderResponse(7L, "ORD-0007", "64888468-1021-70ae-1c1b-36e6f67b3175", "Lavado en seco",
                "CREADO", "2026-10-08", 45000.0, "Providencia", clienteNombre);
    }

    private JsonNode cuerpoEnviado() throws Exception {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(captor.capture());
        assertEquals(QUEUE_URL, captor.getValue().queueUrl());
        return JsonMapper.builder().build().readTree(captor.getValue().messageBody());
    }

    @Test
    void ordenCreadaLlevaElIdentificadorYElNombreLegible() throws Exception {
        publisher.publishCreada(new OrdenCreadaEvent(orden("cliente@cleanfresh.com")));

        JsonNode json = cuerpoEnviado();
        assertEquals("ORDEN_CREADA", json.get("tipo").asString());
        assertEquals("ORD-0007", json.get("numeroOrden").asString());
        assertEquals("64888468-1021-70ae-1c1b-36e6f67b3175", json.get("cliente").asString());
        assertEquals("cliente@cleanfresh.com", json.get("clienteNombre").asString());
        assertEquals("Providencia", json.get("sucursal").asString());
    }

    @Test
    void ordenListaTambienLlevaElNombreLegible() throws Exception {
        publisher.publishLista(new OrdenListaEvent(orden("cliente@cleanfresh.com")));

        JsonNode json = cuerpoEnviado();
        assertEquals("ORDEN_LISTA", json.get("tipo").asString());
        assertEquals("cliente@cleanfresh.com", json.get("clienteNombre").asString());
    }

    @Test
    void sinNombreLegibleElCampoVieneNull() throws Exception {
        publisher.publishCreada(new OrdenCreadaEvent(orden(null)));

        JsonNode json = cuerpoEnviado();
        assertTrue(json.has("clienteNombre") && json.get("clienteNombre").isNull());
        assertEquals("64888468-1021-70ae-1c1b-36e6f67b3175", json.get("cliente").asString());
    }
}
