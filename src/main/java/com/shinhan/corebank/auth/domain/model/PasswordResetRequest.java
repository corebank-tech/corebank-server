package com.shinhan.corebank.auth.domain.model;

import java.time.LocalDateTime;

public class PasswordResetRequest {
    private final String requestId;
    private final Long customerId;
    private final String target;
    private final String codeHash;
    private boolean used;
    private LocalDateTime verifiedAt;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;

    public PasswordResetRequest(
            String requestId,
            Long customerId,
            String target,
            String codeHash,
            boolean used,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.requestId = requestId;
        this.customerId = customerId;
        this.target = target;
        this.codeHash = codeHash;
        this.used = used;
        this.verifiedAt = verifiedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PasswordResetRequest issue(
            String id,
            Long customerId,
            String target,
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new PasswordResetRequest(id, customerId, target, codeHash, false, null, expiresAt, createdAt);
    }

    public void use(LocalDateTime at) {
        this.used = true;
        this.verifiedAt = at;
    }

    public String requestId() {
        return requestId;
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
