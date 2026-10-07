package com.cleanfresh.ms_cleanfresh_orders.event;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;

/**
 * Evento interno: una orden acaba de pasar a DESPACHADO (está lista). Lo
 * publica OrderService y lo recoge OrdenEventListener una vez confirmada la
 * transacción.
 */
public record OrdenListaEvent(OrderResponse orden) {
}
