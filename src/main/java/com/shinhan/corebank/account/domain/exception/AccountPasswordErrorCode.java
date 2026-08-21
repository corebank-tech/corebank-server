package com.shinhan.corebank.account.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// APW 접두어는 P6(계좌비밀번호·OTP 인증) 소유지만, 신규 비밀번호 확인값 일치 검증은 단순
// 문자열 비교라 P6 실구현을 기다릴 필요 없이 여기서 처리한다. 다른 APW 코드(APW0001/APW0101/
// APW0102 등 실제 비밀번호 인증 검증)는 P6 도메인이 착수하면 추가할 것 — 지금은 이 한 건만 필요.
public enum AccountPasswordErrorCode implements ErrorCode {
    NEW_PASSWORD_CONFIRM_MISMATCH("APW0002", 400, "신규 비밀번호와 확인값이 일치하지 않습니다.");

    private final String code;
    private final int status;
    private final String message;

    AccountPasswordErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public int getStatus() { return status; }
    @Override public String getMessage() { return message; }
}
