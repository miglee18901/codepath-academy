package com.codepath.academy.repository;

import com.codepath.academy.entity.OrderItem;
import org.springframework.data.jpa.repository.*;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    java.util.List<OrderItem> findByOrderId(Long orderId);
}
