package com.shinhan.corebank.auth.application.port.out;

// 계좌 본인확인 결과와 최종 식별된 고객 PK를 전달한다.
public record FindIdAccountVerificationResult(
        FindIdAccountVerificationStatus status,
        Long customerId
) {
}
