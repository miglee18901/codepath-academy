package com.example.java_basic.repository;

import com.example.java_basic.entity.Category;
import org.springframework.data.jpa.repository.*;

public interface CategoryRepository extends JpaRepository<Category, Long> {}
