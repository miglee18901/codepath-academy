package com.codepath.academy.repository;

import com.codepath.academy.entity.Lesson;
import org.springframework.data.jpa.repository.*;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.id = :id")
    java.util.Optional<Lesson> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.List<Lesson> findBySectionIdOrderByPositionAscIdAsc(Long sectionId);
}
