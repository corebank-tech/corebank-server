package com.shinhan.corebank.fds.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

/** 행동 흐름 검증 오류를 공통 응답 코드로 변환한다. */
public enum FdsErrorCode implements ErrorCode {
    INVALID_EVENT("FDS0001", 400, "행동 이벤트의 순서 또는 시간이 올바르지 않습니다."),
    INVALID_INVESTIGATION_REPORT("FDS0002", 400, "조사 근거를 확인할 수 없습니다."),
    INVALID_RISK_DATASET("FDS0003", 400, "위험 계좌 데이터셋을 확인할 수 없습니다."),
    AGENT_UNAUTHORIZED("FDS0101", 401, "Agent 서비스를 인증할 수 없습니다."),
    NOT_FOUND("FDS0201", 404, "FDS 자료를 찾을 수 없습니다."),
    FLOW_CONFLICT("FDS0301", 409, "현재 상태에서는 요청을 처리할 수 없습니다."),
    APPROVAL_CALL_REQUIRED("FDS0302", 409, "고객 확인 결과를 기록한 뒤 승인할 수 있습니다.");

    private final String code;
    private final int status;
    private final String message;

    FdsErrorCode(String code, int status, String message) {
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
