package com.codepath.academy.repository;

import com.codepath.academy.entity.LessonProgress;
import org.springframework.data.jpa.repository.*;

public interface LessonProgressRepository
        extends JpaRepository<LessonProgress, Long> {
    java.util.Optional<LessonProgress> findByUserIdAndLessonId(
            Long userId,
            Long lessonId
    );

    java.util.List<LessonProgress> findByUserId(Long userId);
}
