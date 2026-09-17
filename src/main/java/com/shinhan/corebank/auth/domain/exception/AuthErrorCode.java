package com.shinhan.corebank.auth.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 로그인 검증과 계정 잠금에 사용하는 인증 오류 코드
public enum AuthErrorCode implements ErrorCode {
    INVALID_PASSWORD_FORMAT("ATH0001", 400, "비밀번호 규칙에 맞지 않습니다."),
    PASSWORD_CONFIRMATION_MISMATCH("ATH0002", 400, "비밀번호와 확인값이 일치하지 않습니다."),
    PREVIOUS_PASSWORD_REUSE("ATH0003", 400, "직전 비밀번호는 재사용할 수 없습니다."),
    VERIFICATION_CODE_MISMATCH("ATH0007", 400, "인증번호가 일치하지 않습니다."),
    VERIFICATION_CODE_EXPIRED("ATH0008", 400, "인증번호가 만료되었습니다."),
    IDENTITY_INFORMATION_MISMATCH("ATH0009", 400, "실명 또는 계좌 정보가 일치하지 않습니다."),

    LOGIN_FAILED("ATH0101", 401, "아이디 또는 비밀번호가 일치하지 않습니다."),

    ACCOUNT_LOCKED("ATH0102", 403, "비밀번호 5회 오류로 계정이 잠겼습니다."),

    USER_NOT_FOUND("ATH0201", 404, "존재하지 않는 사용자입니다."),
    VERIFICATION_REQUEST_NOT_FOUND("ATH0202", 404, "인증 요청을 찾을 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

    AuthErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
