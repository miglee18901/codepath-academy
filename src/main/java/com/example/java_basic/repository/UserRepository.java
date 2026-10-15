package com.example.java_basic.repository;

import com.example.java_basic.entity.User;
import jakarta.persistence.LockModeType;

import java.util.*;

import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(Long id);
}
