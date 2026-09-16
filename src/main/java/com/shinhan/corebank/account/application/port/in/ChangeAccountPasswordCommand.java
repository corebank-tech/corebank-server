package com.shinhan.corebank.account.application.port.in;

// 계좌비밀번호 변경에 필요한 고객·계좌·인증·신규 비밀번호 정보를 전달한다.
public record ChangeAccountPasswordCommand(
        Long customerId,
        Long accountId,
        String otpAuthToken,
        String accountPasswordAuthToken,
        String newAccountPassword,
        String newAccountPasswordConfirm,
        String requestIp) {}
