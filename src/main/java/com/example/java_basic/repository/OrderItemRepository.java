package com.example.java_basic.repository;

import com.example.java_basic.entity.OrderItem;
import org.springframework.data.jpa.repository.*;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  java.util.List<OrderItem> findByOrderId(Long orderId);
}
