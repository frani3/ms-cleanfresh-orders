package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.OrderEventPublisher;
import com.cleanfresh.ms_cleanfresh_orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OrderEventTests {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderEventPublisher publisher;

    @Test
    void crearOrdenPublicaOrdenCreadaConElNumeroDeOrden() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Evento", "Planchado", 15000.0, "Maipu"));

        ArgumentCaptor<OrdenCreadaEvent> captor = ArgumentCaptor.forClass(OrdenCreadaEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(creada.numeroOrden(), captor.getValue().orden().numeroOrden());
        assertEquals("Cliente Evento", captor.getValue().orden().cliente());
    }

    @Test
    void siFallaLaPublicacionLaOrdenSeCreaIgual() {
        doThrow(new RuntimeException("sqs caido")).when(publisher).publish(any());

        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Sin Cola", "Planchado", 15000.0, "Maipu"));

        assertEquals("CREADO", creada.estado());
        assertTrue(orderService.findById(creada.id()).isPresent());
    }
}
