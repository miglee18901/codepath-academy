package com.codepath.academy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "passkey_credentials")
@Getter @Setter @NoArgsConstructor
public class PasskeyCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, unique = true, length = 1400)
    private String credentialId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyCose;
    @Column(nullable = false)
    private long signatureCount;
    @Column(nullable = false, length = 80)
    private String label;
    @Column(nullable = false)
    private String transports;
    @Column(nullable = false)
    private boolean backupEligible;
    @Column(nullable = false)
    private boolean backedUp;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    @Version
    private long version;
}
