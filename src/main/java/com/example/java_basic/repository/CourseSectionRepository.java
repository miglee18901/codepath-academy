package com.example.java_basic.repository;

import com.example.java_basic.entity.CourseSection;
import org.springframework.data.jpa.repository.*;

public interface CourseSectionRepository
  extends JpaRepository<CourseSection, Long>
{
  java.util.List<CourseSection> findByCourseIdOrderByPositionAscIdAsc(
    Long courseId
  );
}
