package com.shinhan.corebank.account.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum AccountErrorCode implements ErrorCode {

    INVALID_ACCOUNT_ALIAS("ACC0001", 400, "계좌별명 길이 제한을 초과했습니다."),
    INVALID_DISPLAY_ORDER("ACC0002", 400, "표시 순서 정보가 올바르지 않습니다."),
    ACCOUNT_NOT_FOUND_OR_FORBIDDEN("ACC0201", 404, "계좌를 찾을 수 없거나 접근할 수 없습니다."),
    INVALID_ACCOUNT_STATUS("ACC0301", 409, "거래정지 또는 해지 상태의 계좌입니다."),
    WITHDRAWAL_ACCOUNT_UNREGISTRATION_RESTRICTED("ACC0302", 409, "예약이체 또는 자동이체가 등록된 출금계좌는 삭제할 수 없습니다."),
    ACCOUNT_NUMBER_SEQUENCE_EXHAUSTED("ACC0303", 409, "발급 가능한 계좌번호가 모두 사용되었습니다."),
    ACCOUNT_NUMBER_SEQUENCE_NOT_FOUND("ACC9001", 500, "계좌번호 발급 처리 중 오류가 발생했습니다.");

    private final String code;
    private final int status;
    private final String message;

    AccountErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public int    getStatus()  { return status; }
    @Override public String getMessage() { return message; }
}
