package com.codepath.academy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 160)
    private String courseTitle;

    @Column(nullable = false, precision = 12, scale = 0)
    private java.math.BigDecimal price;
}
