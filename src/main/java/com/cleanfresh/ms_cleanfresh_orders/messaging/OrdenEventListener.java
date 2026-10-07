package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica los eventos solo después de que el cambio quedó confirmado en la
 * base (así nunca se avisa de algo que terminó revirtiéndose). Si la
 * publicación falla, el cambio ya existe: se registra el error y no se propaga.
 */
@Component
public class OrdenEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenEventListener.class);

    private final OrderEventPublisher publisher;

    public OrdenEventListener(OrderEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenCreada(OrdenCreadaEvent event) {
        try {
            publisher.publishCreada(event);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar ORDEN_CREADA de {} en SQS; la orden ya quedó creada",
                    event.orden().numeroOrden(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenLista(OrdenListaEvent event) {
        try {
            publisher.publishLista(event);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar ORDEN_LISTA de {} en SQS; el estado ya quedó guardado",
                    event.orden().numeroOrden(), e);
        }
    }
}
