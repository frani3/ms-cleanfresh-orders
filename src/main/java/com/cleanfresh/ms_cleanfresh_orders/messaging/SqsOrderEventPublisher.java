package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deja un mensaje ORDEN_CREADA en la cola de SQS (app.sqs.enabled=true).
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
    public void publish(OrdenCreadaEvent event) {
        OrderResponse orden = event.orden();
        String body = MAPPER.writeValueAsString(new OrdenCreadaMessage(
                "ORDEN_CREADA",
                orden.numeroOrden(),
                orden.cliente(),
                orden.servicio(),
                orden.sucursal(),
                orden.total(),
                orden.fecha()
        ));
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());
    }
}
