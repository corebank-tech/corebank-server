package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

// OTP를 발급할 거래 유형과 비어 있지 않은 핵심 거래정보를 입력받는다.
public record IssueOtpRequest(
        @Schema(description = "OTP로 인증할 거래 유형", example = "IMMEDIATE_TRANSFER") @NotNull
                OtpTransactionType transactionType,
        @Schema(
                        description = "OTP에 결합할 핵심 거래정보. 거래 유형별 필수 필드를 포함해야 한다.",
                        example = "{\"accountId\":101,\"depositAccountNumber\":\"110123456789\",\"amount\":50000}")
                @NotEmpty
                Map<String, Object> transactionData) {
    public IssueOtpCommand toCommand(Long customerId) {
        return new IssueOtpCommand(customerId, transactionType, transactionData);
    }
}
