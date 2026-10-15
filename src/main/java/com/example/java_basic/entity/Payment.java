package com.example.java_basic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long orderId;

  @Column(nullable = false, unique = true, length = 100)
  private String externalRef;

  @Column(nullable = false, precision = 12, scale = 0)
  private java.math.BigDecimal amount;

  @Column(nullable = false)
  private java.time.LocalDateTime paidAt;
}
