package com.cleanfresh.ms_cleanfresh_orders.config;

import com.cleanfresh.ms_cleanfresh_orders.entity.OrderEntity;
import com.cleanfresh.ms_cleanfresh_orders.repository.OrderJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Con la base vacía, carga las mismas 6 órdenes que tenía el servicio en
 * memoria (EP1), para que las respuestas no cambien. Si ya hay datos, no toca nada.
 */
@Component
public class OrderDataInitializer implements CommandLineRunner {

    private final OrderJpaRepository repository;

    public OrderDataInitializer(OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
                orden("ORD-0001", "Maria Gonzalez", "Lavado y secado", "CREADO", "2026-09-10", 15000.0, "Providencia"),
                orden("ORD-0002", "Juan Perez", "Lavado en seco", "ACEPTADO", "2026-09-11", 22000.0, "Ñuñoa"),
                orden("ORD-0003", "Ana Torres", "Planchado", "EN_PREPARACION", "2026-09-12", 8000.0, "Las Condes"),
                orden("ORD-0004", "Carlos Ramirez", "Lavado de edredones", "DESPACHADO", "2026-09-13", 35000.0, "Maipú"),
                orden("ORD-0005", "Laura Diaz", "Lavado y planchado", "ENTREGADO", "2026-09-14", 18000.0, "Providencia"),
                orden("ORD-0006", "Pedro Sanchez", "Lavado en seco", "CANCELADO", "2026-09-15", 20000.0, "Ñuñoa")
        ));
    }

    private static OrderEntity orden(String numeroOrden, String cliente, String servicio, String estado,
                                     String fecha, Double total, String sucursal) {
        return new OrderEntity(numeroOrden, cliente, servicio, estado, LocalDate.parse(fecha), total, sucursal);
    }
}
