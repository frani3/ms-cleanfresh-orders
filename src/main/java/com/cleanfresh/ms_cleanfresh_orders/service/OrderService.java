package com.cleanfresh.ms_cleanfresh_orders.service;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.entity.OrderEntity;
import com.cleanfresh.ms_cleanfresh_orders.repository.OrderJpaRepository;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenCreadaEvent;
import com.cleanfresh.ms_cleanfresh_orders.event.OrdenListaEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderService {

    public static final Set<String> ESTADOS_VALIDOS =
            Set.of("CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO", "CANCELADO");

    // Estado en el que una orden se considera "lista" para avisar al cliente.
    private static final String ESTADO_LISTO = "DESPACHADO";

    private final OrderJpaRepository repository;
    private final ApplicationEventPublisher events;

    public OrderService(OrderJpaRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findById(Long id) {
        return repository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findByEstado(String estado) {
        return repository.findByEstadoIgnoreCaseOrderByIdAsc(estado).stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        OrderEntity order = repository.save(new OrderEntity(
                null,
                request.cliente(),
                request.servicio(),
                "CREADO",
                LocalDate.now(),
                request.total(),
                request.sucursal()
        ));
        // El N° de orden se deriva del id que asigna la base; al ser una entidad
        // gestionada, el cambio se guarda solo al cerrar la transacción.
        order.setNumeroOrden(String.format("ORD-%04d", order.getId()));
        OrderResponse response = toResponse(order);
        // Se avisa a SQS recién cuando la transacción se confirma (ver OrdenEventListener).
        events.publishEvent(new OrdenCreadaEvent(response));
        return response;
    }

    /**
     * Cambia el estado de la orden. Devuelve vacío si no existe y lanza
     * IllegalArgumentException si el estado no es uno de ESTADOS_VALIDOS. Al
     * pasar a DESPACHADO por primera vez publica ORDEN_LISTA (tras confirmarse
     * la transacción).
     */
    @Transactional
    public Optional<OrderResponse> cambiarEstado(String numeroOrden, String estado) {
        String nuevo = estado == null ? "" : estado.trim().toUpperCase();
        if (!ESTADOS_VALIDOS.contains(nuevo)) {
            throw new IllegalArgumentException("Estado inválido: " + estado);
        }
        return repository.findByNumeroOrden(numeroOrden).map(order -> {
            boolean pasaAListo = ESTADO_LISTO.equals(nuevo) && !ESTADO_LISTO.equals(order.getEstado());
            order.setEstado(nuevo);
            OrderResponse response = toResponse(order);
            if (pasaAListo) {
                events.publishEvent(new OrdenListaEvent(response));
            }
            return response;
        });
    }

    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getNumeroOrden(),
                order.getCliente(),
                order.getServicio(),
                order.getEstado(),
                order.getFecha().toString(),
                order.getTotal(),
                order.getSucursal()
        );
    }
}
