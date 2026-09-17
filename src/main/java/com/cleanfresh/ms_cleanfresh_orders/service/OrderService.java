package com.cleanfresh.ms_cleanfresh_orders.service;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final List<OrderResponse> orders = List.of(
            new OrderResponse(1L, "ORD-0001", "Maria Gonzalez", "Lavado y secado", "CREADO", "2026-09-10", 15000.0),
            new OrderResponse(2L, "ORD-0002", "Juan Perez", "Lavado en seco", "ACEPTADO", "2026-09-11", 22000.0),
            new OrderResponse(3L, "ORD-0003", "Ana Torres", "Planchado", "EN_PREPARACION", "2026-09-12", 8000.0),
            new OrderResponse(4L, "ORD-0004", "Carlos Ramirez", "Lavado de edredones", "DESPACHADO", "2026-09-13", 35000.0),
            new OrderResponse(5L, "ORD-0005", "Laura Diaz", "Lavado y planchado", "ENTREGADO", "2026-09-14", 18000.0),
            new OrderResponse(6L, "ORD-0006", "Pedro Sanchez", "Lavado en seco", "CANCELADO", "2026-09-15", 20000.0)
    );

    public List<OrderResponse> findAll() {
        return orders;
    }

    public Optional<OrderResponse> findById(Long id) {
        return orders.stream()
                .filter(order -> order.id().equals(id))
                .findFirst();
    }

    public List<OrderResponse> findByEstado(String estado) {
        return orders.stream()
                .filter(order -> order.estado().equalsIgnoreCase(estado))
                .toList();
    }
}
