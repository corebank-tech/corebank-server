package com.shinhan.corebank.limit.application.port.out;

/**
 * limit 모듈 전용 인증 토큰 검증 포트. 한도 변경(REQ-TRSF-025)은 2단계 인증을 요구하므로
 * (api_conventions.md §8-2) 계좌비밀번호 토큰과 OTP 토큰을 모두 검증한다.
 *
 * <p>transfer·autotransfer·scheduledtransfer 의 같은 이름 포트는 계좌를 기준으로 검증하지만,
 * 이체한도는 계좌가 아니라 고객 단위 자원이라(transfer_limit 의 PK 가 customer_id) 대조할
 * 계좌가 없다. §8-3 도 한도 API 를 계좌 소유권 규칙의 제외 대상으로 두고 "계좌 ID 를 받지
 * 않고"라고 명시한다. 그래서 고객 기준으로 검증한다.
 *
 * <p>customerId 는 세션에서 얻은 값을 넘긴다. 클라이언트가 보낸 값을 신뢰하지 않는다(REQ-NFR-007).
 */
public interface AuthTokenVerificationPort {

    /** 계좌비밀번호 검증으로 발급된 토큰(§6-3)이 이 고객의 것인지 확인한다. 위반 시 APW0102. */
    void verifyAccountPassword(String authToken, Long customerId);

    /**
     * OTP 토큰을 검증하고 일회용으로 소비한다. 인증 당시 등록한 거래내용과 지금 바꾸려는 한도가
     * 같은지 대조하므로(§8-2 ④) 인증 통과 후 금액을 바꿔치기할 수 없다.
     * 토큰 무효는 OTP0101, 거래내용 불일치는 OTP0102 다.
     */
    void verifyOtp(String otpAuthToken, Long customerId, long oneTimeLimit, long dailyLimit);
}
