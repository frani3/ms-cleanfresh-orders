package com.cleanfresh.ms_cleanfresh_orders.messaging;

/**
 * Cuerpo (JSON) del mensaje que se deja en la cola. Es el contrato con
 * ms-cleanfresh-notificaciones, que define su propia copia de este record.
 * {@code tipo} distingue ORDEN_CREADA de ORDEN_LISTA; el resto de los campos
 * son los mismos en ambos.
 */
public record OrdenMessage(
        String tipo,
        String numeroOrden,
        String cliente,
        String servicio,
        String sucursal,
        Double total,
        String fecha
) {
}
