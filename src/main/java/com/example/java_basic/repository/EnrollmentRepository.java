package com.example.java_basic.repository;

import com.example.java_basic.entity.Enrollment;
import org.springframework.data.jpa.repository.*;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    java.util.List<Enrollment> findByUserIdOrderByIdDesc(Long userId);
}
