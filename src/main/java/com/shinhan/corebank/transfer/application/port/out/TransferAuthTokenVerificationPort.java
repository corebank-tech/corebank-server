package com.shinhan.corebank.transfer.application.port.out;

/**
 * transfer 모듈 전용 인증 토큰(Account-Password-Auth-Token) 검증 포트.
 * autotransfer.AuthTokenVerificationPort와 이름·시그니처는 같지만 모듈 간 참조 금지 원칙에
 * 따라 별도 인터페이스다. 실행 API는 평문 비밀번호/OTP를 받지 않고, 사전에 발급된 인증 완료
 * 토큰만 받아 여기서 검증한다(REQ-TRSF-009).
 */
public interface TransferAuthTokenVerificationPort {
    void verify(String authToken, Long accountId, String purpose);
}
