package com.codepath.academy.repository;

import com.codepath.academy.entity.User;
import jakarta.persistence.LockModeType;

import java.util.*;

import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPasskeyHandle(String passkeyHandle);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(Long id);
}
