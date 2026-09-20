package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordCommand;

public record LoginPasswordChangeRequest(String currentPassword, String newPassword, String newPasswordConfirm) {

    // 로그인 고객과 요청 IP를 서버에서 주입해 변경 명령을 생성한다.
    public ChangeLoginPasswordCommand toCommand(Long customerId, String userId, String requestIp) {
        return new ChangeLoginPasswordCommand(
                customerId, userId, currentPassword, newPassword, newPasswordConfirm, requestIp);
    }
}
