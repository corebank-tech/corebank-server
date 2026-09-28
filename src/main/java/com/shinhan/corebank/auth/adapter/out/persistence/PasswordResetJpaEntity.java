package com.shinhan.corebank.auth.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_request")
public class PasswordResetJpaEntity {
    @Id
    @Column(name = "verification_request_id", length = 64)
    private String id;

    @Column(name = "purpose", nullable = false, length = 24)
    private String purpose;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "target", length = 100)
    private String target;

    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;

    @Column(name = "error_count", nullable = false)
    private byte errorCount;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PasswordResetJpaEntity() {}

    public PasswordResetJpaEntity(
            String id,
            Long customerId,
            String target,
            String codeHash,
            boolean used,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.id = id;
        this.purpose = "PASSWORD_RESET";
        this.customerId = customerId;
        this.target = target;
        this.codeHash = codeHash;
        this.used = used;
        this.verifiedAt = verifiedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public Long customerId() {
        return customerId;
    }

    public String target() {
        return target;
    }

    public String codeHash() {
        return codeHash;
    }

    public boolean used() {
        return used;
    }

    public LocalDateTime verifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
