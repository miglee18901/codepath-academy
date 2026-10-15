package com.example.java_basic.repository;

import com.example.java_basic.entity.Course;
import org.springframework.data.jpa.repository.*;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCategoryId(Long categoryId);
}
