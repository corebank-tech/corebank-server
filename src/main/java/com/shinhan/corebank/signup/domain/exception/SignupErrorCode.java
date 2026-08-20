package com.shinhan.corebank.signup.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 회원가입 과정에서 사용하는 업무 오류 코드와 메시지를 정의한다.
public enum SignupErrorCode implements ErrorCode {

    INVALID_USER_ID_FORMAT(
            "ATH0004",
            400,
            "아이디 형식이 올바르지 않습니다."
    ),

    REQUIRED_TERMS_NOT_AGREED(
            "ATH0006",
            400,
            "필수 약관에 동의하지 않았습니다."
    ),

    EMAIL_VERIFICATION_CODE_MISMATCH(
            "ATH0007",
            400,
            "인증번호가 일치하지 않습니다."
    ),

    EMAIL_VERIFICATION_EXPIRED(
            "ATH0008",
            400,
            "인증번호가 만료되었습니다."
    ),

    ACCOUNT_VERIFICATION_FAILED(
            "ATH0009",
            400,
            "실명 또는 계좌 정보가 일치하지 않습니다."
    ),

    ACCOUNT_LOCKED(
            "ATH0102",
            403,
            "비밀번호 5회 오류로 계정이 잠겼습니다."
    ),

    INVALID_EMAIL_VERIFICATION_TOKEN(
            "ATH0103",
            403,
            "이메일 인증 토큰이 유효하지 않습니다."
    ),

    INVALID_TERMS_AUTH_TOKEN(
            "ATH0104",
            403,
            "약관 동의 토큰이 유효하지 않습니다."
    ),

    INVALID_ACCOUNT_AUTH_TOKEN(
            "ATH0105",
            403,
            "계좌 인증 토큰이 유효하지 않습니다."
    ),

    EMAIL_VERIFICATION_REQUEST_NOT_FOUND(
            "ATH0202",
            404,
            "인증 요청을 찾을 수 없습니다."
    ),

    DUPLICATE_USER_ID(
            "ATH0301",
            409,
            "이미 사용 중인 아이디입니다."
    ),

    DUPLICATE_EMAIL(
            "ATH0302",
            409,
            "이미 가입된 이메일입니다."
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
