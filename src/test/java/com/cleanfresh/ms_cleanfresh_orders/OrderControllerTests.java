package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    private OrderResponse nuevaOrden() {
        return orderService.create(new OrderRequest("Cliente Http", "Planchado", 9500.0, "Providencia"));
    }

    @Test
    void cambiarEstadoDevuelveLaOrdenActualizada() throws Exception {
        OrderResponse orden = nuevaOrden();

        mockMvc.perform(put("/api/orders/{n}/estado", orden.numeroOrden())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"aceptado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOrden").value(orden.numeroOrden()))
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
    }

    @Test
    void estadoFueraDelFlujoDevuelve400() throws Exception {
        OrderResponse orden = nuevaOrden();

        mockMvc.perform(put("/api/orders/{n}/estado", orden.numeroOrden())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"VOLANDO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordenInexistenteDevuelve404() throws Exception {
        mockMvc.perform(put("/api/orders/ORD-9999/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACEPTADO\"}"))
                .andExpect(status().isNotFound());
    }
}
