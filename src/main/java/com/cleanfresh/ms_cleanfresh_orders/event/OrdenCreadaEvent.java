package com.cleanfresh.ms_cleanfresh_orders.event;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;

/**
 * Evento interno: se acaba de crear una orden. Lo publica OrderService y lo
 * recoge OrdenEventListener una vez confirmada la transacción.
 */
public record OrdenCreadaEvent(OrderResponse orden) {
}
