package com.example.java_basic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long categoryId;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 12, scale = 0)
  private java.math.BigDecimal price;

  @Column(length = 1000)
  private String thumbnailUrl;

  @Column(nullable = false, length = 100)
  private String instructor;

  @Column(nullable = false, length = 30)
  private String level;

  @Column(nullable = false)
  private boolean published;
}
