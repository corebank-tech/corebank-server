package com.shinhan.corebank.otp.application.port.in;

import com.shinhan.corebank.otp.api.OtpTransactionType;

import java.util.Map;

// 로그인 고객의 OTP 발급 대상 거래정보를 전달한다.
public record IssueOtpCommand(
        Long customerId,
        OtpTransactionType transactionType,
        Map<String, Object> transactionData
) {
}
