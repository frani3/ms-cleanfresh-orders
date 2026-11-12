package com.cleanfresh.ms_cleanfresh_orders.messaging.kafka;

/**
 * Evento de negocio que {@code orders} publica en {@code orders.events} (Spec 044). Mismo sobre que los comandos de RabbitMQ
 * (type, eventId, timestamp, traceId, correlationId, payload), pero con los nombres de evento del caso: OrderCreated, OrderAccepted,
 * OrderPreparing, OrderDispatched, OrderDelivered y OrderCancelled. Es el contrato con {@code reportes} y {@code auditoria}, que
 * tienen su propia copia de este record.
 *
 * @param type          qué pasó (OrderCreated, OrderAccepted, ...)
 * @param eventId       identificador único del evento; es la clave de idempotencia de los consumidores
 * @param timestamp     cuándo ocurrió (ISO-8601, UTC)
 * @param traceId       agrupa todo lo que se dispara desde una misma operación
 * @param correlationId el número de orden
 * @param payload       los datos de la orden en ese momento
 */
public record OrderEventMessage(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        Payload payload
) {

    /**
     * {@code estado} es el estado de la orden después del evento; {@code fechaHora} es cuándo se creó la orden (null en las
     * anteriores a la Spec 036); {@code actor} es quien provocó el evento.
     */
    public record Payload(
            String numeroOrden,
            String cliente,
            String clienteNombre,
            String servicio,
            String sucursal,
            Double total,
            String estado,
            String fecha,
            String fechaHora,
            String actor
    ) {
    }
}
