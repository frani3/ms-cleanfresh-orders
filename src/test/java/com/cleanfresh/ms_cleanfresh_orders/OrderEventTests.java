package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
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
        doThrow(new RuntimeException("rabbitmq caido")).when(publisher).publishCreada(any());

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

    // ---------- Spec 035: un aviso al Cliente por cada cambio de estado ----------

    private java.util.List<String> estadosAvisados() {
        ArgumentCaptor<OrdenEstadoEvent> captor = ArgumentCaptor.forClass(OrdenEstadoEvent.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publishEstado(captor.capture());
        return captor.getAllValues().stream().map(e -> e.orden().estado()).toList();
    }

    @Test
    void cadaCambioDeEstadoAvisaConElEstadoNuevo() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Otros", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO");
        orderService.cambiarEstado(creada.numeroOrden(), "EN_PREPARACION");
        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO");
        orderService.cambiarEstado(creada.numeroOrden(), "ENTREGADO");

        assertEquals(java.util.List.of("ACEPTADO", "EN_PREPARACION", "ENTREGADO"), estadosAvisados());
        // DESPACHADO sigue siendo ORDEN_LISTA (aviso y boleta), no ORDEN_ESTADO
        verify(publisher, times(1)).publishLista(any());
    }

    @Test
    void cancelarTambienAvisaAlCliente() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Cancela", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "CANCELADO");

        assertEquals(java.util.List.of("CANCELADO"), estadosAvisados());
        ArgumentCaptor<OrdenEstadoEvent> captor = ArgumentCaptor.forClass(OrdenEstadoEvent.class);
        verify(publisher).publishEstado(captor.capture());
        assertEquals(creada.numeroOrden(), captor.getValue().orden().numeroOrden());
        assertEquals("Cliente Cancela", captor.getValue().orden().cliente());
    }

    @Test
    void saltarEstadosNoSeImpideYElClienteSeEnteraDelEstadoAlQueLlego() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Salto", "Planchado", 9500.0, "Providencia"));

        // de CREADO directo a ENTREGADO, sin pasar por DESPACHADO
        orderService.cambiarEstado(creada.numeroOrden(), "ENTREGADO");

        assertEquals("ENTREGADO", orderService.findById(creada.id()).orElseThrow().estado());
        assertEquals(java.util.List.of("ENTREGADO"), estadosAvisados());
        verify(publisher, never()).publishLista(any());
    }

    @Test
    void repetirElMismoEstadoNoAvisaDeNuevo() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Repite", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO");
        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO");
        orderService.cambiarEstado(creada.numeroOrden(), "aceptado");

        assertEquals(java.util.List.of("ACEPTADO"), estadosAvisados());
    }

    @Test
    void volverACreadoNoGeneraAviso() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Vuelve", "Planchado", 9500.0, "Providencia"));
        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO");

        orderService.cambiarEstado(creada.numeroOrden(), "CREADO");

        assertEquals(java.util.List.of("ACEPTADO"), estadosAvisados());
    }

    @Test
    void unEstadoInvalidoONoExistenteNoAvisaNada() {
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Invalido", "Planchado", 9500.0, "Providencia"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> orderService.cambiarEstado(creada.numeroOrden(), "VOLANDO"));
        assertTrue(orderService.cambiarEstado("ORD-9999", "ACEPTADO").isEmpty());

        verify(publisher, never()).publishEstado(any());
        verify(publisher, never()).publishLista(any());
    }

    @Test
    void siFallaLaPublicacionDelAvisoDeEstadoElEstadoQuedaGuardado() {
        doThrow(new RuntimeException("rabbitmq caido")).when(publisher).publishEstado(any());
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Estado Sin Cola", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "EN_PREPARACION");

        assertEquals("EN_PREPARACION", orderService.findById(creada.id()).orElseThrow().estado());
    }

    @Test
    void siFallaLaPublicacionDeListaElEstadoQuedaGuardado() {
        doThrow(new RuntimeException("rabbitmq caido")).when(publisher).publishLista(any());
        OrderResponse creada = orderService.create(
                new OrderRequest("Cliente Lista Sin Cola", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO");

        assertEquals("DESPACHADO", orderService.findById(creada.id()).orElseThrow().estado());
    }

    @Test
    void elEventoDeOrdenCreadaLlevaElNombreLegible() {
        orderService.create(new OrderRequest("id-cognito-1", "Planchado", 9500.0, "Providencia", "Cliente Demo"));

        ArgumentCaptor<OrdenCreadaEvent> captor = ArgumentCaptor.forClass(OrdenCreadaEvent.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publishCreada(captor.capture());
        OrdenCreadaEvent ultimo = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("id-cognito-1", ultimo.orden().cliente());
        assertEquals("Cliente Demo", ultimo.orden().clienteNombre());
    }
}
