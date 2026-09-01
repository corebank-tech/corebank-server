package com.shinhan.corebank.account.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 계좌비밀번호 검증과 인증 토큰 오류를 정의한다.
public enum AccountPasswordErrorCode implements ErrorCode {
    PASSWORD_MISMATCH("APW0001", 400, "계좌비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_CONFIRM_MISMATCH("APW0002", 400, "신규 비밀번호와 확인값이 일치하지 않습니다."),
    PASSWORD_LOCKED("APW0101", 403, "계좌비밀번호 5회 오류로 거래가 정지되었습니다."),
    INVALID_AUTH_TOKEN("APW0102", 403, "계좌비밀번호 인증 토큰이 유효하지 않습니다.");

    private final String code;
    private final int status;
    private final String message;

    AccountPasswordErrorCode(String code, int status, String message) {
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
