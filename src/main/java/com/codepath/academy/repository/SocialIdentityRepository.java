package com.codepath.academy.repository;

import com.codepath.academy.entity.SocialIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {
    Optional<SocialIdentity> findByProviderAndSubject(String provider, String subject);
    List<SocialIdentity> findByUserId(Long userId);
    boolean existsByUserIdAndProvider(Long userId, String provider);
}
