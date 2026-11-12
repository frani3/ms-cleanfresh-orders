package com.cleanfresh.ms_cleanfresh_orders.event;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;

/**
 * Evento interno: una orden pasó a DESPACHADO (está lista). Genera el aviso al Cliente y la boleta. Lo
 * publica OrderService y lo recoge OrdenEventListener una vez confirmada la
 * transacción. {@code actor} es quien hizo el cambio (auditoría, Spec 044).
 */
public record OrdenListaEvent(OrderResponse orden, Actor actor) {

    public OrdenListaEvent(OrderResponse orden) {
        this(orden, Actor.DESCONOCIDO);
    }
}
