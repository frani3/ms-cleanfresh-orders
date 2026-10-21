package com.cleanfresh.ms_cleanfresh_orders.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spec 038: el panel de RabbitMQ muestra a este servicio por su nombre. */
class RabbitConfigTests {

    @Test
    void laConexionSeLlamaComoElServicio() throws Exception {
        assertEquals("ms-cleanfresh-orders",
                new RabbitConfig().nombreDeLaConexion().obtainNewConnectionName(new CachingConnectionFactory()));
    }
}
