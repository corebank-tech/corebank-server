package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;
import io.swagger.v3.oas.annotations.media.Schema;

// 선택적으로 변경할 휴대폰 번호·이메일과 이메일 인증 토큰을 받는다.
public record CustomerInfoUpdateRequest(
        @Schema(
                        description = "변경할 휴대폰 번호. 변경하지 않으면 null",
                        example = "01012345678",
                        pattern = "^\\d{11}$",
                        nullable = true)
                String phoneNumber,
        @Schema(description = "변경할 이메일. 변경하지 않으면 null", example = "new-user@mail.com", nullable = true) String email,
        @Schema(
                        description = "이메일 변경 시 필요한 1회성 이메일 인증 토큰",
                        example = "EMAIL_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n",
                        nullable = true)
                String emailVerificationToken) {

    // 세션 고객 식별자를 더해 application command로 변환한다.
    public UpdateCustomerInfoCommand toCommand(Long customerId) {
        return new UpdateCustomerInfoCommand(customerId, phoneNumber, email, emailVerificationToken);
    }
}
