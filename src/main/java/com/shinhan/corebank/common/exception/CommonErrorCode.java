package com.shinhan.corebank.common.exception;

public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT("CMN0001", 400, "입력값이 올바르지 않습니다."),
    REQUIRED_FIELD_MISSING("CMN0002", 400, "필수 입력값이 누락되었습니다."),
    INVALID_DATE_RANGE("CMN0003", 400, "조회 시작일이 종료일보다 늦습니다."),
    DATE_RANGE_EXCEEDED("CMN0004", 400, "조회기간이 최대 1년을 초과했습니다."),
    INVALID_PAGE_SIZE("CMN0005", 400, "지원하지 않는 페이지 크기입니다."),

    UNAUTHORIZED("CMN0101", 401, "인증정보가 없거나 세션이 만료되었습니다."),
    FORBIDDEN("CMN0102", 403, "해당 자원에 접근할 권한이 없습니다."),

    INVALID_ENDPOINT("CMN0201", 404, "잘못된 요청 주소입니다."),

    DUPLICATE_REQUEST_IN_PROGRESS("CMN0301", 409, "동일 요청이 처리 중입니다."),
    IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST("CMN0302", 409, "동일한 멱등키가 다른 요청에 사용되었습니다."),
    CONCURRENT_MODIFICATION("CMN0303", 409, "다른 요청에 의해 정보가 변경되었습니다. 다시 조회한 후 시도해 주세요."),

    INTERNAL_ERROR("CMN9999", 500, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final int status;
    private final String message;

    CommonErrorCode(String code, int status, String message) {
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
