package com.shinhan.corebank.otp.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// OTP 발급·검증·인증 토큰 처리에 사용하는 오류 코드를 정의한다.
public enum OtpErrorCode implements ErrorCode {

    OTP_CODE_MISMATCH("OTP0001", 400, "OTP 번호가 일치하지 않습니다."),
    INVALID_AUTH_TOKEN("OTP0101", 403, "OTP 인증 토큰이 유효하지 않습니다."),
    TRANSACTION_MISMATCH("OTP0102", 403, "인증한 거래 내용과 요청 내용이 일치하지 않습니다."),
    ATTEMPTS_EXCEEDED(
            "OTP0103",
            403,
            "OTP 오류 횟수(5회)를 초과하여 잠금 처리되었습니다. OTP를 재발급받아 주세요."
    ),
    OTP_EXPIRED("OTP0104", 403, "OTP가 만료되었습니다. 재발급받아 주세요."),
    OTP_REQUEST_NOT_FOUND("OTP0201", 404, "OTP 요청을 찾을 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

    OtpErrorCode(String code, int status, String message) {
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
