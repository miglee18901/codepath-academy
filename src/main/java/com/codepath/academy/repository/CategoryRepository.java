package com.codepath.academy.repository;

import com.codepath.academy.entity.Category;
import org.springframework.data.jpa.repository.*;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
