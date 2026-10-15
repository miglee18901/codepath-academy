package com.example.java_basic.repository;

import com.example.java_basic.entity.Lesson;
import org.springframework.data.jpa.repository.*;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
  java.util.List<Lesson> findBySectionIdOrderByPositionAscIdAsc(Long sectionId);
}
