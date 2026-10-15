package com.codepath.academy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class CourseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 0)
    private java.math.BigDecimal total;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String checkoutUrl;

    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime paidAt;
}
