package com.cleanfresh.ms_cleanfresh_orders.repository;

import com.cleanfresh.ms_cleanfresh_orders.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByOrderByIdAsc();

    List<OrderEntity> findByEstadoIgnoreCaseOrderByIdAsc(String estado);
}
