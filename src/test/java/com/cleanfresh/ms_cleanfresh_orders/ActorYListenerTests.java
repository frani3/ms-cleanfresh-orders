package com.cleanfresh.ms_cleanfresh_orders;

import com.cleanfresh.ms_cleanfresh_orders.controller.OrderController;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.Actor;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenEstadoEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import com.cleanfresh.ms_cleanfresh_orders.messaging.OrdenEventListener;
import com.cleanfresh.ms_cleanfresh_orders.messaging.OrderEventPublisher;
import com.cleanfresh.ms_cleanfresh_orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 044: quién hizo cada acción (auditoría) y varios publicadores a la vez (RabbitMQ y Kafka). */
class ActorYListenerTests {

    private static OrderResponse orden() {
        return new OrderResponse(7L, "ORD-0007", "cliente-uuid", "Planchado", "CREADO", "2026-10-15", 9500.0, "Providencia", null);
    }

    // ---------- Actor ----------

    @Test
    void actorSinDatosEsDesconocido() {
        assertEquals(Actor.DESCONOCIDO, Actor.de(null, null));
        assertEquals(Actor.DESCONOCIDO, Actor.de("  ", ""));
    }

    @Test
    void actorConUsuarioYOrigenLosConserva() {
        Actor actor = Actor.de(" op-1 ", " 190.5.59.49 ");

        assertEquals("op-1", actor.usuario());
        assertEquals("190.5.59.49", actor.origen());
    }

    @Test
    void actorSoloConOrigenQuedaConUsuarioDesconocido() {
        Actor actor = Actor.de(null, "10.0.0.1");

        assertEquals("desconocido", actor.usuario());
        assertEquals("10.0.0.1", actor.origen());
    }

    @Test
    void actorSoloConUsuarioNoTieneOrigen() {
        assertNull(Actor.de("op-1", null).origen());
    }

    // ---------- el controlador lee las cabeceras del BFF ----------

    @Test
    void elControladorPasaElActorDeLasCabecerasAlServicio() throws Exception {
        OrderService servicio = mock(OrderService.class);
        when(servicio.cambiarEstado(eq("ORD-0007"), eq("ACEPTADO"), any(Actor.class))).thenReturn(Optional.of(orden()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(servicio)).build();

        mvc.perform(put("/api/orders/ORD-0007/estado").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor", "op-1234").header("X-Origen", "190.5.59.49")
                        .content("{\"estado\":\"ACEPTADO\"}"))
                .andExpect(status().isOk());

        verify(servicio).cambiarEstado("ORD-0007", "ACEPTADO", new Actor("op-1234", "190.5.59.49"));
    }

    @Test
    void sinCabecerasElActorEsDesconocido() throws Exception {
        OrderService servicio = mock(OrderService.class);
        when(servicio.create(any(OrderRequest.class), any(Actor.class))).thenReturn(orden());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(servicio)).build();

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"cliente-uuid\",\"servicio\":\"Planchado\",\"total\":9500,\"sucursal\":\"Providencia\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<Actor> captor = ArgumentCaptor.forClass(Actor.class);
        verify(servicio).create(any(OrderRequest.class), captor.capture());
        assertEquals(Actor.DESCONOCIDO, captor.getValue());
    }

    // ---------- varios publicadores ----------

    @Test
    void siUnPublicadorFallaElOtroIgualRecibeElEvento() {
        OrderEventPublisher rabbit = mock(OrderEventPublisher.class);
        OrderEventPublisher kafka = mock(OrderEventPublisher.class);
        OrdenEventListener listener = new OrdenEventListener(List.of(rabbit, kafka));
        OrdenCreadaEvent creada = new OrdenCreadaEvent(orden());
        OrdenListaEvent lista = new OrdenListaEvent(orden());
        OrdenEstadoEvent estado = new OrdenEstadoEvent(orden());
        doThrow(new RuntimeException("rabbit caido")).when(rabbit).publishCreada(any());
        doThrow(new RuntimeException("rabbit caido")).when(rabbit).publishLista(any());
        doThrow(new RuntimeException("rabbit caido")).when(rabbit).publishEstado(any());

        assertDoesNotThrow(() -> {
            listener.onOrdenCreada(creada);
            listener.onOrdenLista(lista);
            listener.onOrdenEstado(estado);
        });

        verify(kafka).publishCreada(creada);
        verify(kafka).publishLista(lista);
        verify(kafka).publishEstado(estado);
    }
}
