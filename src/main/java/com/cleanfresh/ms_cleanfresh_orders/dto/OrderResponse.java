package com.cleanfresh.ms_cleanfresh_orders.dto;

/**
 * {@code clienteNombre} es el nombre legible del cliente y puede ser null (órdenes
 * anteriores a la Spec 032, o creadas sin que se pudiera resolver): en ese caso se
 * muestra {@code cliente}. {@code fechaHora} es el instante de creación (ISO-8601, UTC) y es null en las órdenes
 * anteriores a la Spec 036, que solo tienen {@code fecha}.
 */
public record OrderResponse(
        Long id,
        String numeroOrden,
        String cliente,
        String servicio,
        String estado,
        String fecha,
        Double total,
        String sucursal,
        String clienteNombre,
        String fechaHora
) {

    public OrderResponse(Long id, String numeroOrden, String cliente, String servicio, String estado,
                         String fecha, Double total, String sucursal, String clienteNombre) {
        this(id, numeroOrden, cliente, servicio, estado, fecha, total, sucursal, clienteNombre, null);
    }
}
