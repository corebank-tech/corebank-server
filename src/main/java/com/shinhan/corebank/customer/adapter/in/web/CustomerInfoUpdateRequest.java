package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;

// 선택적으로 변경할 휴대폰 번호·이메일과 이메일 인증 토큰을 받는다.
public record CustomerInfoUpdateRequest(
        String phoneNumber,
        String email,
        String emailVerificationToken
) {

    // 세션 고객 식별자를 더해 application command로 변환한다.
    public UpdateCustomerInfoCommand toCommand(Long customerId) {
        return new UpdateCustomerInfoCommand(
                customerId,
                phoneNumber,
                email,
                emailVerificationToken
        );
    }
}
