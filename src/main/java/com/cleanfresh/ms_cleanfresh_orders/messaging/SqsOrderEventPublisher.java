package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deja los mensajes ORDEN_CREADA y ORDEN_LISTA en la cola de SQS
 * (app.sqs.enabled=true).
 */
@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true")
public class SqsOrderEventPublisher implements OrderEventPublisher {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SqsClient sqs;
    private final String queueUrl;

    public SqsOrderEventPublisher(SqsClient sqs, @Value("${app.sqs.queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publishCreada(OrdenCreadaEvent event) {
        enviar("ORDEN_CREADA", event.orden());
    }

    @Override
    public void publishLista(OrdenListaEvent event) {
        enviar("ORDEN_LISTA", event.orden());
    }

    private void enviar(String tipo, OrderResponse orden) {
        String body = MAPPER.writeValueAsString(new OrdenMessage(
                tipo,
                orden.numeroOrden(),
                orden.cliente(),
                orden.clienteNombre(),
                orden.servicio(),
                orden.sucursal(),
                orden.total(),
                orden.fecha()
        ));
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());
    }
}
