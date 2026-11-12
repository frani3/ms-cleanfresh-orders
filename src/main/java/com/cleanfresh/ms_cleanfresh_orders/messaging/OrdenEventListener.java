package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Publica los eventos solo después de que el cambio quedó confirmado en la
 * base (así nunca se avisa de algo que terminó revirtiéndose). Los publicadores activos son
 * independientes (RabbitMQ para los comandos, Kafka para los eventos de negocio; Spec 044): si uno falla se registra el
 * error y se sigue con el siguiente. El cambio ya existe, así que nada se propaga.
 */
@Component
public class OrdenEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrdenEventListener.class);

    private final List<OrderEventPublisher> publishers;

    public OrdenEventListener(List<OrderEventPublisher> publishers) {
        this.publishers = publishers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenCreada(OrdenCreadaEvent event) {
        for (OrderEventPublisher publisher : publishers) {
            try {
                publisher.publishCreada(event);
            } catch (RuntimeException e) {
                log.error("{} no pudo publicar ORDEN_CREADA de {}; la orden ya quedó creada",
                        publisher.getClass().getSimpleName(), event.orden().numeroOrden(), e);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenLista(OrdenListaEvent event) {
        for (OrderEventPublisher publisher : publishers) {
            try {
                publisher.publishLista(event);
            } catch (RuntimeException e) {
                log.error("{} no pudo publicar ORDEN_LISTA de {}; el estado ya quedó guardado",
                        publisher.getClass().getSimpleName(), event.orden().numeroOrden(), e);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenEstado(OrdenEstadoEvent event) {
        for (OrderEventPublisher publisher : publishers) {
            try {
                publisher.publishEstado(event);
            } catch (RuntimeException e) {
                log.error("{} no pudo publicar el aviso de estado {} de {}; el estado ya quedó guardado",
                        publisher.getClass().getSimpleName(), event.orden().estado(), event.orden().numeroOrden(), e);
            }
        }
    }
}
