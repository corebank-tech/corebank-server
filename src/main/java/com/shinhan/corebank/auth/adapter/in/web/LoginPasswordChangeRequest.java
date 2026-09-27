package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginPasswordChangeRequest(
        @Schema(
                        description = "현재 로그인 비밀번호",
                        example = "Current1!",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currentPassword,
        @Schema(
                        description = "신규 로그인 비밀번호(8~15자, 영문자·숫자·특수문자 포함 및 금칙 규칙 적용)",
                        example = "NewPass8!x",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String newPassword,
        @Schema(
                        description = "신규 로그인 비밀번호 확인",
                        example = "NewPass8!x",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String newPasswordConfirm) {

    // 로그인 고객과 요청 IP를 서버에서 주입해 변경 명령을 생성한다.
    public ChangeLoginPasswordCommand toCommand(Long customerId, String userId, String requestIp) {
        return new ChangeLoginPasswordCommand(
                customerId, userId, currentPassword, newPassword, newPasswordConfirm, requestIp);
    }
}
