package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        verify(publisher).publishCreada(captor.capture());
        assertEquals(creada.numeroOrden(), captor.getValue().orden().numeroOrden());
        assertEquals("Cliente Evento", captor.getValue().orden().cliente());
    }

    @Test
    void siFallaLaPublicacionLaOrdenSeCreaIgual() {
        doThrow(new RuntimeException("sqs caido")).when(publisher).publishCreada(any());

        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Sin Cola", "Planchado", 15000.0, "Maipu"));

        assertEquals("CREADO", creada.estado());
        assertTrue(orderService.findById(creada.id()).isPresent());
    }

    @Test
    void pasarADespachadoPublicaOrdenListaUnaSolaVez() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Listo", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO");
        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO");

        ArgumentCaptor<OrdenListaEvent> captor = ArgumentCaptor.forClass(OrdenListaEvent.class);
        verify(publisher, times(1)).publishLista(captor.capture());
        assertEquals(creada.numeroOrden(), captor.getValue().orden().numeroOrden());
        assertEquals("DESPACHADO", captor.getValue().orden().estado());
        assertEquals("Cliente Listo", captor.getValue().orden().cliente());
    }

    @Test
    void otrosEstadosNoPublicanNada() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Otros", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO");
        orderService.cambiarEstado(creada.numeroOrden(), "EN_PREPARACION");
        orderService.cambiarEstado(creada.numeroOrden(), "ENTREGADO");

        verify(publisher, never()).publishLista(any());
    }

    @Test
    void siFallaLaPublicacionDeListaElEstadoQuedaGuardado() {
        doThrow(new RuntimeException("sqs caido")).when(publisher).publishLista(any());
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Lista Sin Cola", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO");

        assertEquals("DESPACHADO", orderService.findById(creada.id()).orElseThrow().estado());
    }
}
