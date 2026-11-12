package com.cleanfresh.ms_cleanfresh_orders.messaging.kafka;

/**
 * Registro de auditoría que se publica en {@code audit.timeline} (Spec 044): quién hizo qué, sobre qué, cuándo y desde dónde.
 * Lo consume {@code auditoria}, que tiene su propia copia de este record.
 *
 * @param eventId   identificador único del registro (clave de idempotencia del consumidor)
 * @param timestamp cuándo ocurrió (ISO-8601, UTC)
 * @param traceId   el mismo del evento de negocio asociado
 * @param actor     quién lo hizo (el {@code username} de Cognito)
 * @param accion    qué hizo (OrderCreated, OrderAccepted, ...)
 * @param entidad   sobre qué tipo de cosa (orden, servicio)
 * @param entidadId su identificador (número de orden o id del servicio)
 * @param detalle   texto legible con el contexto
 * @param origen    desde dónde (IP del cliente), o null
 * @param fuente    qué microservicio lo registró
 */
public record AuditEntryMessage(
        String eventId,
        String timestamp,
        String traceId,
        String actor,
        String accion,
        String entidad,
        String entidadId,
        String detalle,
        String origen,
        String fuente
) {
}
