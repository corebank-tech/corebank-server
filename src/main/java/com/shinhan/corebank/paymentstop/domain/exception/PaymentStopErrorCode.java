package com.shinhan.corebank.paymentstop.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

// 지급정지 사건과 이의제기 심사(D-36). api_conventions.md §4-18 과 같이 고친다.
public enum PaymentStopErrorCode implements ErrorCode {
    DISPUTED_TRANSFER_NOT_DEPOSIT("PST0001", 400, "다툼 거래가 지급정지 계좌의 입금 거래가 아닙니다."),
    OBJECTION_DEADLINE_PASSED("PST0002", 400, "이의제기 신청 기한이 지났습니다."),
    OBJECTION_REQUIREMENTS_MISSING("PST0003", 400, "신분증 사본 확인과 이의제기 사유가 필요합니다."),
    EARLY_RELEASE_BASIS_REQUIRED("PST0004", 400, "수용과 일부 지급정지 판정에는 조기 해제 근거가 필요합니다."),
    EVIDENCE_OUT_OF_RANGE("PST0005", 400, "판정안의 근거 거래가 조회 범위 밖입니다."),
    SUPPLEMENT_LIMIT_EXCEEDED("PST0006", 400, "보완 요청은 2회까지입니다."),
    DOCUMENT_INVALID("PST0007", 400, "파일 형식, 크기, 개수가 허용 범위를 벗어났습니다."),
    WORKER_AUTH_FAILED("PST0101", 401, "워커 인증에 실패했습니다."),
    JOB_TOKEN_INVALID("PST0102", 403, "작업 토큰이 유효하지 않습니다."),
    CASE_NOT_FOUND("PST0201", 404, "지급정지 사건을 찾을 수 없습니다."),
    OBJECTION_NOT_FOUND("PST0202", 404, "이의제기를 찾을 수 없습니다."),
    AGENT_RUN_NOT_FOUND("PST0203", 404, "분석 실행을 찾을 수 없습니다."),
    PENDING_DECISION_NOT_FOUND("PST0204", 404, "승인할 판정이 없습니다."),
    DOCUMENT_NOT_FOUND("PST0205", 404, "문서를 찾을 수 없습니다."),
    RELEASE_NOT_ALLOWED("PST0301", 409, "해제 요건을 충족하지 않아 이 판정을 확정할 수 없습니다."),
    OBJECTION_ALREADY_DECIDED("PST0302", 409, "이미 판정이 확정된 이의제기입니다."),
    DISPUTED_TRANSFER_ALREADY_REGISTERED("PST0303", 409, "다른 지급정지 사건에 이미 등록된 다툼 거래입니다."),
    OBJECTION_ALREADY_RECEIVED("PST0304", 409, "이미 이의제기가 접수된 사건입니다."),
    STEP_OUT_OF_ORDER("PST0305", 409, "분석 단계 순번이 맞지 않습니다."),
    AGENT_RUN_IN_PROGRESS("PST0306", 409, "진행 중인 분석이 있어 다시 요청할 수 없습니다."),
    STEP_CONFLICT("PST0307", 409, "같은 순번에 다른 단계가 이미 있습니다."),
    INVALID_OBJECTION_STATE("PST0308", 409, "지금 상태에서 할 수 없는 요청입니다."),
    APPROVER_SAME_AS_AUTHOR("PST0309", 409, "판정 작성자는 그 판정을 승인할 수 없습니다."),
    DISCRETIONARY_NOT_ALLOWED("PST0310", 409, "재량 판정으로도 넘을 수 없는 요건입니다."),
    DISCRETIONARY_ACK_REQUIRED("PST0311", 409, "재량 판정 확인이 필요합니다.");

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
