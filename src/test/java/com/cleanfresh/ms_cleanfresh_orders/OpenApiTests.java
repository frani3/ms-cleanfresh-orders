package com.cleanfresh.ms_cleanfresh_orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 046: el servicio publica su contrato OpenAPI (Swagger). */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicaElContratoConSusEndpointsYSuDescripcion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ms-cleanfresh-orders"))
                .andExpect(jsonPath("$.paths['/api/orders']").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{numeroOrden}/estado'].put.summary").exists())
                .andExpect(jsonPath("$.tags[0].name").value("Órdenes"));
    }

    @Test
    void sirveLaInterfazDeSwagger() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
