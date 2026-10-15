package com.example.java_basic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "course_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private java.time.LocalDateTime enrolledAt;
}
