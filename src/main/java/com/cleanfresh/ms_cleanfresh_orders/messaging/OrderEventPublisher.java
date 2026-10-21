package com.cleanfresh.ms_cleanfresh_orders.messaging;

import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;

/**
 * Salida de eventos de órdenes hacia el exterior. La implementación real
 * publica en RabbitMQ; si está desactivado, no hace nada.
 */
public interface OrderEventPublisher {

    void publishCreada(OrdenCreadaEvent event);

    void publishLista(OrdenListaEvent event);

    /** Aviso al Cliente por un cambio de estado distinto de DESPACHADO (Spec 035). */
    void publishEstado(OrdenEstadoEvent event);
}
