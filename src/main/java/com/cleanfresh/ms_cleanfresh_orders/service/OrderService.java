package com.cleanfresh.ms_cleanfresh_orders.service;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.entity.OrderEntity;
import com.cleanfresh.ms_cleanfresh_orders.repository.OrderJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderJpaRepository repository;

    public OrderService(OrderJpaRepository repository) {
        this.repository = repository;
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
        return toResponse(order);
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
