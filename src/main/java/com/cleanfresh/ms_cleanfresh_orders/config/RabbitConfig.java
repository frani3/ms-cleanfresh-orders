package com.cleanfresh.ms_cleanfresh_orders.config;

import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le pone nombre a la conexión de este servicio con RabbitMQ (Spec 038): en el panel, la pestaña
 * <b>Connections</b> muestra {@code ms-cleanfresh-orders} en vez de solo una IP y un puerto, así se distingue del
 * servicio de notificaciones, que corre en la misma máquina. Solo con RabbitMQ habilitado (app.rabbit.enabled=true).
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitConfig {

    public static final String NOMBRE_SERVICIO = "ms-cleanfresh-orders";

    @Bean
    public ConnectionNameStrategy nombreDeLaConexion() {
        return conexionFactory -> NOMBRE_SERVICIO;
    }
}
