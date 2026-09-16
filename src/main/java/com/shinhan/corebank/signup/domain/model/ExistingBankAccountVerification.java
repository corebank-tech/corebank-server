package com.shinhan.corebank.signup.domain.model;

// 기존 은행 고객·계좌 검증 결과와 실패 횟수를 전달한다.
public record ExistingBankAccountVerification(
        ExistingBankAccountVerificationStatus status,
        String existingBankCustomerId,
        String existingBankAccountId,
        int errorCount,
        int remainingAttempts) {

    private static final int MAX_ATTEMPTS = 5;

    public static ExistingBankAccountVerification verified(String customerId, String accountId) {
        return new ExistingBankAccountVerification(
                ExistingBankAccountVerificationStatus.VERIFIED, customerId, accountId, 0, MAX_ATTEMPTS);
    }

    public static ExistingBankAccountVerification informationMismatch() {
        return new ExistingBankAccountVerification(
                ExistingBankAccountVerificationStatus.INFORMATION_MISMATCH, null, null, 0, 0);
    }

    public static ExistingBankAccountVerification passwordMismatch(int errorCount) {
        return new ExistingBankAccountVerification(
                ExistingBankAccountVerificationStatus.PASSWORD_MISMATCH,
                null,
                null,
                errorCount,
                Math.max(0, MAX_ATTEMPTS - errorCount));
    }

    public static ExistingBankAccountVerification locked() {
        return new ExistingBankAccountVerification(
                ExistingBankAccountVerificationStatus.LOCKED, null, null, MAX_ATTEMPTS, 0);
    }
}
