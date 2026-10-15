package com.codepath.academy.repository;

import com.codepath.academy.entity.CourseOrder;
import org.springframework.data.jpa.repository.*;

public interface CourseOrderRepository
        extends JpaRepository<CourseOrder, Long> {
    java.util.List<CourseOrder> findByUserIdOrderByIdDesc(Long userId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CourseOrder o where o.id=:id")
    java.util.Optional<CourseOrder> lockById(Long id);
}
