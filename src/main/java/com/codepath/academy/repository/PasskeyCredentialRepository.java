package com.codepath.academy.repository;

import com.codepath.academy.entity.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {
    List<PasskeyCredential> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<PasskeyCredential> findByCredentialId(String credentialId);
    Optional<PasskeyCredential> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
}
