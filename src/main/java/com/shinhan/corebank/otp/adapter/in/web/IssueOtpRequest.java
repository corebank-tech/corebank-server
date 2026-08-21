package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

// OTP를 발급할 거래 유형과 비어 있지 않은 핵심 거래정보를 입력받는다.
public record IssueOtpRequest(
        @NotNull OtpTransactionType transactionType,
        @NotEmpty Map<String, Object> transactionData
) {
    public IssueOtpCommand toCommand(Long customerId) {
        return new IssueOtpCommand(customerId, transactionType, transactionData);
    }
}
