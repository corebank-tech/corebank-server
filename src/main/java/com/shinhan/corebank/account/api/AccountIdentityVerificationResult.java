package com.shinhan.corebank.account.api;

// 계좌 기반 본인확인 결과와 확인된 고객 식별자를 반환한다.
public record AccountIdentityVerificationResult(
        AccountIdentityVerificationStatus status,
        Long customerId
) {
}
