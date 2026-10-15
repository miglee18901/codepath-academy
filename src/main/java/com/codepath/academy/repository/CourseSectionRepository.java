package com.codepath.academy.repository;

import com.codepath.academy.entity.CourseSection;
import org.springframework.data.jpa.repository.*;

public interface CourseSectionRepository
        extends JpaRepository<CourseSection, Long> {
    java.util.List<CourseSection> findByCourseIdOrderByPositionAscIdAsc(
            Long courseId
    );
}
