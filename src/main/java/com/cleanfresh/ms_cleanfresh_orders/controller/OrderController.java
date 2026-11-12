package com.cleanfresh.ms_cleanfresh_orders.controller;

import com.cleanfresh.ms_cleanfresh_orders.dto.EstadoRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderRequest;
import com.cleanfresh.ms_cleanfresh_orders.dto.OrderResponse;
import com.cleanfresh.ms_cleanfresh_orders.event.Actor;
import com.cleanfresh.ms_cleanfresh_orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    public List<OrderResponse> getByEstado(@PathVariable String estado) {
        return orderService.findByEstado(estado);
    }

    // X-Actor y X-Origen los manda el BFF (username del token e IP del cliente) para la auditoría (Spec 044).
    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody OrderRequest request,
                                                @RequestHeader(value = "X-Actor", required = false) String actor,
                                                @RequestHeader(value = "X-Origen", required = false) String origen) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, Actor.de(actor, origen)));
    }

    @PutMapping("/{numeroOrden}/estado")
    public ResponseEntity<OrderResponse> cambiarEstado(
            @PathVariable String numeroOrden, @RequestBody EstadoRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        try {
            return orderService.cambiarEstado(numeroOrden, request.estado(), Actor.de(actor, origen))
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
