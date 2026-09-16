package com.shinhan.corebank.limit.application.port.in.dto;

import lombok.Builder;

/**
 * 한도 변경 요청(REQ-TRSF-025). 계좌비밀번호·OTP 인증 토큰을 함께 받는다.
 * 정책 상한(POL-015·016) 검사는 요청 DTO 의 Bean Validation 이 담당한다.
 */
@Builder
public record TransferLimitCommand(
        long oneTimeLimit, long dailyLimit, String accountPasswordAuthToken, String otpAuthToken) {}
