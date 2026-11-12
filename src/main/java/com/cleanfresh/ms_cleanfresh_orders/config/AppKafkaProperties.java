package com.cleanfresh.ms_cleanfresh_orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka de este servicio (Spec 044). {@code enabled} lo enciende (por defecto apagado, como RabbitMQ: en local y en las pruebas no
 * hace falta un clúster). Los nombres de los tópicos viven acá y se pueden cambiar por configuración; la conexión (brokers,
 * serializers, acks...) está en {@code spring.kafka.*} de application.yaml.
 */
@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(boolean enabled, Topics topics) {

    public AppKafkaProperties {
        topics = topics == null ? new Topics(null, null) : topics;
    }

    public record Topics(String ordersEvents, String auditTimeline) {

        public Topics {
            ordersEvents = ordersEvents == null || ordersEvents.isBlank() ? "orders.events" : ordersEvents;
            auditTimeline = auditTimeline == null || auditTimeline.isBlank() ? "audit.timeline" : auditTimeline;
        }
    }
}
