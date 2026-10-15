package com.codepath.academy.repository;

import com.codepath.academy.entity.Course;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select c from Course c
            where (:all = true or c.published = true)
              and (:categoryId is null or c.categoryId = :categoryId)
              and (lower(c.title) like :pattern escape '!'
                   or lower(c.description) like :pattern escape '!')
            """)
    Page<Course> searchCatalog(boolean all, Long categoryId, String pattern, Pageable pageable);
}
