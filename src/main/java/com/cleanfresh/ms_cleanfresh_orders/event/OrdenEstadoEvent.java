package com.cleanfresh.ms_cleanfresh_orders.event;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;

/**
 * Evento interno (Spec 035): una orden cambió de estado a ACEPTADO, EN_PREPARACION, ENTREGADO o CANCELADO.
 * {@code orden.estado()} trae el estado nuevo. El paso a DESPACHADO tiene su propio evento
 * ({@link OrdenListaEvent}, que además genera la boleta). Lo publica OrderService y lo recoge
 * OrdenEventListener una vez confirmada la transacción.
 */
public record OrdenEstadoEvent(OrderResponse orden) {
}
