package com.cleanfresh.ms_cleanfresh_orders.dto;

/**
 * Pedido de una orden nueva. {@code clienteNombre} es opcional: el nombre legible
 * del cliente (lo resuelve el BFF desde Cognito); {@code cliente} sigue siendo el
 * identificador estable que dice de quién es la orden.
 */
public record OrderRequest(
        String cliente,
        String servicio,
        Double total,
        String sucursal,
        String clienteNombre
) {

    public OrderRequest(String cliente, String servicio, Double total, String sucursal) {
        this(cliente, servicio, total, sucursal, null);
    }
}
