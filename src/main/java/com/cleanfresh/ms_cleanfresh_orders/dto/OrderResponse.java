package com.cleanfresh.ms_cleanfresh_orders.dto;

public record OrderResponse(
        Long id,
        String numeroOrden,
        String cliente,
        String servicio,
        String estado,
        String fecha,
        Double total
) {
}
