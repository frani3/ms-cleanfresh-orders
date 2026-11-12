package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.Actor;
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
import static org.mockito.Mockito.verify;

/** Spec 044: el servicio entrega al publicador quién hizo cada acción, para el registro de auditoría. */
@SpringBootTest
class OrderServiceActorTests {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private OrderEventPublisher publisher;

    private static final Actor OPERADOR = new Actor("op-1234", "190.5.59.49");

    @Test
    void crearConActorLoLlevaEnElEvento() {
        orderService.create(new OrderRequest("cliente-uuid-1", "Planchado", 9500.0, "Providencia"), OPERADOR);

        ArgumentCaptor<OrdenCreadaEvent> captor = ArgumentCaptor.forClass(OrdenCreadaEvent.class);
        verify(publisher).publishCreada(captor.capture());
        assertEquals(OPERADOR, captor.getValue().actor());
    }

    @Test
    void crearSinActorToma_alClienteDeLaOrden() {
        orderService.create(new OrderRequest("cliente-uuid-2", "Planchado", 9500.0, "Providencia"));

        ArgumentCaptor<OrdenCreadaEvent> captor = ArgumentCaptor.forClass(OrdenCreadaEvent.class);
        verify(publisher).publishCreada(captor.capture());
        assertEquals("cliente-uuid-2", captor.getValue().actor().usuario());
    }

    @Test
    void cambiarEstadoLlevaElActorEnCadaTipoDeEvento() {
        OrderResponse creada = orderService.create(new OrderRequest("cliente-uuid-3", "Planchado", 9500.0, "Providencia"));

        orderService.cambiarEstado(creada.numeroOrden(), "ACEPTADO", OPERADOR);
        orderService.cambiarEstado(creada.numeroOrden(), "DESPACHADO", OPERADOR);

        ArgumentCaptor<OrdenEstadoEvent> estado = ArgumentCaptor.forClass(OrdenEstadoEvent.class);
        verify(publisher).publishEstado(estado.capture());
        assertEquals(OPERADOR, estado.getValue().actor());
        ArgumentCaptor<OrdenListaEvent> lista = ArgumentCaptor.forClass(OrdenListaEvent.class);
        verify(publisher).publishLista(lista.capture());
        assertEquals(OPERADOR, lista.getValue().actor());
    }
}
