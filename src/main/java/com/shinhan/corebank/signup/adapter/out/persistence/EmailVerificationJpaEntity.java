package com.shinhan.corebank.signup.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 공용 verification_request 테이블의 이메일 인증 필드를 매핑한다.
@Entity
@Table(name = "verification_request")
public class EmailVerificationJpaEntity {

    @Id
    @Column(name = "verification_request_id", length = 64)
    private String verificationRequestId;

    @Column(name = "purpose", nullable = false, length = 24)
    private String purpose;

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

    @Column(name = "verified_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected EmailVerificationJpaEntity() {}

    public EmailVerificationJpaEntity(
            String verificationRequestId,
            String purpose,
            String target,
            String codeHash,
            byte errorCount,
            boolean locked,
            boolean used,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.verificationRequestId = verificationRequestId;
        this.purpose = purpose;
        this.target = target;
        this.codeHash = codeHash;
        this.errorCount = errorCount;
        this.locked = locked;
        this.used = used;
        this.verifiedAt = verifiedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getVerificationRequestId() {
        return verificationRequestId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getTarget() {
        return target;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public byte getErrorCount() {
        return errorCount;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isUsed() {
        return used;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
