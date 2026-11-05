package com.shinhan.corebank.paymentstop.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 지급정지 사건과 이의제기 심사(D-36). api_conventions.md §4-18 과 같이 고친다.
public enum PaymentStopErrorCode implements ErrorCode {
    DISPUTED_TRANSFER_NOT_DEPOSIT("PST0001", 400, "다툼 거래가 지급정지 계좌의 입금 거래가 아닙니다."),
    OBJECTION_DEADLINE_PASSED("PST0002", 400, "이의제기 신청 기한이 지났습니다."),
    OBJECTION_REQUIREMENTS_MISSING("PST0003", 400, "신분증 사본 확인과 이의제기 사유가 필요합니다."),
    EARLY_RELEASE_BASIS_REQUIRED("PST0004", 400, "수용과 일부 지급정지 판정에는 조기 해제 근거가 필요합니다."),
    CASE_NOT_FOUND("PST0201", 404, "지급정지 사건을 찾을 수 없습니다."),
    OBJECTION_NOT_FOUND("PST0202", 404, "이의제기를 찾을 수 없습니다."),
    RELEASE_NOT_ALLOWED("PST0301", 409, "해제 요건을 충족하지 않아 이 판정을 확정할 수 없습니다."),
    OBJECTION_ALREADY_DECIDED("PST0302", 409, "이미 판정이 확정된 이의제기입니다."),
    DISPUTED_TRANSFER_ALREADY_REGISTERED("PST0303", 409, "다른 지급정지 사건에 이미 등록된 다툼 거래입니다."),
    OBJECTION_ALREADY_RECEIVED("PST0304", 409, "이미 이의제기가 접수된 사건입니다.");

    private final String code;
    private final int status;
    private final String message;

    PaymentStopErrorCode(String code, int status, String message) {
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
