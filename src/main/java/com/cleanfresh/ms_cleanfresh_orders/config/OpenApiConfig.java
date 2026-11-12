package com.cleanfresh.ms_cleanfresh_orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI del servicio: {@code /swagger-ui.html} y {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("ms-cleanfresh-orders")
                .version("0.0.1")
                .description("Órdenes de Clean&Fresh Manager. Guarda las órdenes en PostgreSQL, publica sus comandos en RabbitMQ y sus eventos de "
                        + "negocio y auditoría en Kafka. No es público: solo lo consulta el BFF."));
    }
}
