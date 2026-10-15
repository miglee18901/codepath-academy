package com.example.java_basic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "lesson_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long lessonId;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private long positionSeconds;

    @Column(nullable = false)
    private long durationSeconds;

    @Column(nullable = false)
    private long positionUpdatedAt;

    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;
}
