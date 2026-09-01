package com.shinhan.corebank.customer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 고객정보 변경에서 사용하는 업무 오류 코드와 메시지를 정의한다.
public enum CustomerErrorCode implements ErrorCode {
    INVALID_PHONE_NUMBER("MYP0001", 400, "휴대폰 번호 형식이 올바르지 않습니다."),

    DUPLICATE_EMAIL("ATH0302", 409, "이미 가입된 이메일입니다.");

    private final String code;
    private final int status;
    private final String message;

    CustomerErrorCode(String code, int status, String message) {
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
