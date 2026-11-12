package com.cleanfresh.ms_cleanfresh_orders.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configuración de Kafka de {@code orders} (Spec 044). El productor (acks=all, idempotente, JSON tipado...) se configura en
 * {@code spring.kafka.producer.*}; acá solo está el hilo que envía.
 */
@Configuration
@EnableConfigurationProperties(AppKafkaProperties.class)
public class KafkaConfig {

    public static final String NOMBRE_SERVICIO = "ms-cleanfresh-orders";
    private static final int COLA_MAXIMA = 10_000;

    /**
     * Un solo hilo y una cola acotada: publicar en Kafka nunca hace esperar a quien creó o cambió la orden (la respuesta HTTP no
     * depende del clúster), y un solo hilo conserva el orden de los eventos. Si la cola se llena (Kafka caído por mucho tiempo), el
     * evento se descarta y se registra; el cambio de la orden ya está guardado.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
    public ExecutorService kafkaEnvio() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(COLA_MAXIMA),
                runnable -> {
                    Thread hilo = new Thread(runnable, "kafka-envio");
                    hilo.setDaemon(true);
                    return hilo;
                },
                (tarea, pool) -> org.slf4j.LoggerFactory.getLogger(KafkaConfig.class)
                        .error("Cola de envío a Kafka llena ({} pendientes): se descarta un evento", COLA_MAXIMA));
    }
}
