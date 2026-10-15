package com.codepath.academy.repository;

import com.codepath.academy.entity.Course;
import org.springframework.data.jpa.repository.*;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCategoryId(Long categoryId);
}
