package com.cleanfresh.ms_cleanfresh_orders.messaging;

/**
 * Envelope común de todos los comandos que se publican en RabbitMQ (Spec 033). Es el contrato con
 * ms-cleanfresh-notificaciones, que define su propia copia de este record.
 *
 * @param type          qué pasó (ORDEN_CREADA, ORDEN_LISTA u ORDEN_ESTADO)
 * @param eventId       identificador único del mensaje; es la clave de idempotencia del consumidor
 * @param timestamp     cuándo se publicó (ISO-8601, UTC)
 * @param traceId       agrupa todo lo que se dispara desde una misma operación
 * @param correlationId el número de orden
 * @param payload       los datos de la orden
 */
public record Envelope(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        OrdenPayload payload
) {

    /**
     * Datos de la orden. {@code cliente} es el identificador de Cognito (de quién es la orden) y
     * {@code clienteNombre} el nombre legible, que puede ser null. {@code estado} es el estado de la orden al
     * publicar (en ORDEN_ESTADO, el estado nuevo del que se avisa).
     */
    public record OrdenPayload(
            String numeroOrden,
            String cliente,
            String clienteNombre,
            String servicio,
            String sucursal,
            Double total,
            String fecha,
            String estado
    ) {
    }
}
