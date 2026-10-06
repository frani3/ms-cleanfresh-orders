package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderServiceTests {

    @Autowired
    private OrderService orderService;

    @Test
    void siembraLasOrdenesDeEp1EnOrdenDeId() {
        var ordenes = orderService.findAll();

        assertTrue(ordenes.size() >= 6);
        assertEquals("ORD-0001", ordenes.get(0).numeroOrden());
        assertEquals("Providencia", ordenes.get(0).sucursal());
        assertEquals("2026-09-10", ordenes.get(0).fecha());
    }

    @Test
    void crearOrdenLaPersisteConNumeroDerivadoDelId() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Test Cliente", "Planchado", 15000.0, "Maipú"));

        assertEquals("CREADO", creada.estado());
        assertEquals(String.format("ORD-%04d", creada.id()), creada.numeroOrden());

        OrderResponse leida = orderService.findById(creada.id()).orElseThrow();
        assertEquals("Test Cliente", leida.cliente());
        assertEquals(creada.numeroOrden(), leida.numeroOrden());
    }

    @Test
    void filtraPorEstadoSinImportarMayusculas() {
        var entregadas = orderService.findByEstado("entregado");

        assertTrue(entregadas.stream().allMatch(o -> o.estado().equals("ENTREGADO")));
        assertTrue(entregadas.size() >= 1);
    }
}
