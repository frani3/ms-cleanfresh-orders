package com.cleanfresh.ms_cleanfresh_orders.service;

import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    // En memoria, sin DB cloud todavía (ver CLAUDE.md -> Pendientes). Se
    // vuelve mutable porque ahora hay un POST real (Spec 025) que agrega
    // pedidos nuevos, ademas de los 6 mock originales.
    private final List<OrderResponse> orders = new CopyOnWriteArrayList<>(List.of(
            new OrderResponse(1L, "ORD-0001", "Maria Gonzalez", "Lavado y secado", "CREADO", "2026-09-10", 15000.0, "Providencia"),
            new OrderResponse(2L, "ORD-0002", "Juan Perez", "Lavado en seco", "ACEPTADO", "2026-09-11", 22000.0, "Ñuñoa"),
            new OrderResponse(3L, "ORD-0003", "Ana Torres", "Planchado", "EN_PREPARACION", "2026-09-12", 8000.0, "Las Condes"),
            new OrderResponse(4L, "ORD-0004", "Carlos Ramirez", "Lavado de edredones", "DESPACHADO", "2026-09-13", 35000.0, "Maipú"),
            new OrderResponse(5L, "ORD-0005", "Laura Diaz", "Lavado y planchado", "ENTREGADO", "2026-09-14", 18000.0, "Providencia"),
            new OrderResponse(6L, "ORD-0006", "Pedro Sanchez", "Lavado en seco", "CANCELADO", "2026-09-15", 20000.0, "Ñuñoa")
    ));

    private final AtomicLong nextId = new AtomicLong(orders.size());

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

    public OrderResponse create(OrderRequest request) {
        long id = nextId.incrementAndGet();
        String numeroOrden = String.format("ORD-%04d", id);
        OrderResponse created = new OrderResponse(
                id,
                numeroOrden,
                request.cliente(),
                request.servicio(),
                "CREADO",
                LocalDate.now().toString(),
                request.total(),
                request.sucursal()
        );
        orders.add(created);
        return created;
    }
}
