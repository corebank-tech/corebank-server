package com.shinhan.corebank.otp.api;

import java.util.Map;

// 최종 거래가 OTP 인증 당시 거래내용과 같은지 검증하는 요청이다.
public record OtpAuthTokenVerification(
        String otpAuthToken,
        Long customerId,
        OtpTransactionType transactionType,
        Map<String, Object> transactionData) {}
