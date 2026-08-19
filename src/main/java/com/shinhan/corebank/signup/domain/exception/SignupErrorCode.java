package com.shinhan.corebank.signup.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum SignupErrorCode implements ErrorCode {

    REQUIRED_TERMS_NOT_AGREED(
            "ATH0006",
            400,
            "필수 약관에 동의하지 않았습니다."
    ),

    INVALID_TERMS_AUTH_TOKEN(
            "ATH0104",
            403,
            "약관 동의 토큰이 유효하지 않습니다."
    );

    private final String code;
    private final int status;
    private final String message;

    SignupErrorCode(
            String code,
            int status,
            String message
    ) {
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