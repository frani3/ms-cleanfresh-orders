package com.cleanfresh.ms_cleanfresh_orders.dto;

public record OrderRequest(
        String cliente,
        String servicio,
        Double total,
        String sucursal
) {
}
