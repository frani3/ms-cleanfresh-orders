package com.cleanfresh.ms_cleanfresh_orders.repository;

import com.cleanfresh.ms_cleanfresh_orders.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByOrderByIdAsc();

    List<OrderEntity> findByEstadoIgnoreCaseOrderByIdAsc(String estado);

    Optional<OrderEntity> findByNumeroOrden(String numeroOrden);
}
