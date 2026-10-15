package com.example.java_basic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_sections")
@Getter
@Setter
@NoArgsConstructor
public class CourseSection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long courseId;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(nullable = false)
  private int position;
}
