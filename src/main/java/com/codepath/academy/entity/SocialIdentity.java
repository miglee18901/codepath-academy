package com.codepath.academy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "social_identities")
@Getter @Setter @NoArgsConstructor
public class SocialIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false, length = 100)
    private String providerEmail;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
