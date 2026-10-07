package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publicador por defecto (app.sqs.enabled=false): no envía nada.
 */
@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "false", matchIfMissing = true)
public class NoopOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopOrderEventPublisher.class);

    @Override
    public void publishCreada(OrdenCreadaEvent event) {
        log.debug("SQS desactivado: no se publica ORDEN_CREADA de {}", event.orden().numeroOrden());
    }

    @Override
    public void publishLista(OrdenListaEvent event) {
        log.debug("SQS desactivado: no se publica ORDEN_LISTA de {}", event.orden().numeroOrden());
    }
}
