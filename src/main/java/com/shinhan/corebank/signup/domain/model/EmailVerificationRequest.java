package com.shinhan.corebank.signup.domain.model;

import java.time.LocalDateTime;

// 이메일 인증번호의 해시와 만료·사용 상태를 관리한다.
public class EmailVerificationRequest {

    private final String verificationRequestId;
    private final EmailVerificationPurpose purpose;
    private final String target;
    private final String codeHash;
    private final int errorCount;
    private final boolean locked;
    private boolean used;
    private LocalDateTime verifiedAt;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;

    public EmailVerificationRequest(
            String verificationRequestId,
            EmailVerificationPurpose purpose,
            String target,
            String codeHash,
            int errorCount,
            boolean locked,
            boolean used,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
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

    public static EmailVerificationRequest issue(
            String verificationRequestId,
            EmailVerificationPurpose purpose,
            String target,
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        return new EmailVerificationRequest(
                verificationRequestId,
                purpose,
                target,
                codeHash,
                0,
                false,
                false,
                null,
                expiresAt,
                createdAt
        );
    }

    public void verify(LocalDateTime verifiedAt) {
        this.used = true;
        this.verifiedAt = verifiedAt;
    }

    public String verificationRequestId() {
        return verificationRequestId;
    }

    public EmailVerificationPurpose purpose() {
        return purpose;
    }

    public String target() {
        return target;
    }

    public String codeHash() {
        return codeHash;
    }

    public int errorCount() {
        return errorCount;
    }

    public boolean locked() {
        return locked;
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
