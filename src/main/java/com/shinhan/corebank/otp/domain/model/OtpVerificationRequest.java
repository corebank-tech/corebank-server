package com.shinhan.corebank.otp.domain.model;

import com.shinhan.corebank.otp.api.OtpTransactionType;

import java.time.LocalDateTime;
import java.util.Objects;

// verification_request의 OTP 요청 상태와 검증 규칙을 관리한다.
public class OtpVerificationRequest {

    private static final int MAX_ATTEMPTS = 5;

    private final String verificationRequestId;
    private final Long customerId;
    private final OtpTransactionType transactionType;
    private final String canonicalTransactionData;
    private final String codeHash;
    private int errorCount;
    private boolean locked;
    private boolean used;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiresAt;
    private final LocalDateTime createdAt;

    public OtpVerificationRequest(
            String verificationRequestId,
            Long customerId,
            OtpTransactionType transactionType,
            String canonicalTransactionData,
            String codeHash,
            int errorCount,
            boolean locked,
            boolean used,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        this.verificationRequestId = Objects.requireNonNull(verificationRequestId);
        this.customerId = Objects.requireNonNull(customerId);
        this.transactionType = Objects.requireNonNull(transactionType);
        this.canonicalTransactionData = Objects.requireNonNull(canonicalTransactionData);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.errorCount = errorCount;
        this.locked = locked;
        this.used = used;
        this.verifiedAt = verifiedAt;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        validateState();
    }

    // 신규 OTP 요청을 오류 횟수 0과 미사용 상태로 생성한다.
    public static OtpVerificationRequest issue(
            String requestId,
            Long customerId,
            OtpTransactionType transactionType,
            String canonicalTransactionData,
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        return new OtpVerificationRequest(
                requestId,
                customerId,
                transactionType,
                canonicalTransactionData,
                codeHash,
                0,
                false,
                false,
                null,
                expiresAt,
                createdAt
        );
    }

    // 요청이 현재 로그인 고객의 요청인지 확인한다.
    public boolean belongsTo(Long customerId) {
        return this.customerId.equals(customerId);
    }

    // OTP 요청이 180초 유효시간을 초과했는지 확인한다.
    public boolean expiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    // OTP 오답 횟수를 증가시키고 5회 도달 시 요청을 잠근다.
    public OtpAttemptResult recordFailure() {
        if (used || locked) {
            throw new IllegalStateException("사용 완료되거나 잠긴 OTP 요청입니다.");
        }
        errorCount = Math.min(MAX_ATTEMPTS, errorCount + 1);
        locked = errorCount == MAX_ATTEMPTS;
        return currentAttemptResult();
    }

    // 현재 누적 오류 횟수와 남은 횟수를 응답용 결과로 반환한다.
    public OtpAttemptResult currentAttemptResult() {
        return new OtpAttemptResult(
                errorCount,
                MAX_ATTEMPTS - errorCount,
                locked
        );
    }

    // OTP 검증 성공 상태와 검증 완료 시각을 기록한다.
    public void verify(LocalDateTime now) {
        if (used || locked || expiredAt(now)) {
            throw new IllegalStateException("검증할 수 없는 OTP 요청입니다.");
        }
        used = true;
        verifiedAt = now;
    }

    private void validateState() {
        if (errorCount < 0 || errorCount > MAX_ATTEMPTS) {
            throw new IllegalStateException("OTP 오류 횟수는 0회 이상 5회 이하여야 합니다.");
        }
        if (locked != (errorCount == MAX_ATTEMPTS)) {
            throw new IllegalStateException("OTP 오류 횟수와 잠금 상태가 일치하지 않습니다.");
        }
        if (used != (verifiedAt != null)) {
            throw new IllegalStateException("OTP 사용 상태와 검증 시각이 일치하지 않습니다.");
        }
    }

    public String verificationRequestId() { return verificationRequestId; }
    public Long customerId() { return customerId; }
    public OtpTransactionType transactionType() { return transactionType; }
    public String canonicalTransactionData() { return canonicalTransactionData; }
    public String codeHash() { return codeHash; }
    public int errorCount() { return errorCount; }
    public boolean locked() { return locked; }
    public boolean used() { return used; }
    public LocalDateTime verifiedAt() { return verifiedAt; }
    public LocalDateTime expiresAt() { return expiresAt; }
    public LocalDateTime createdAt() { return createdAt; }
}
