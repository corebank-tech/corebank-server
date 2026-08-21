package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("PRD0201", 404, "상품을 찾을 수 없습니다."),
    TERMS_NOT_FOUND("PRD0202", 404, "약관을 찾을 수 없습니다."),

    // 도메인 내부 오류(9000~9999). FK·상위 검증으로 정상 흐름에선 도달 불가
    PRODUCT_TERMS_NOT_RESOLVED("PRD9001", 500, "상품 약관 정보를 확인할 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

    ProductErrorCode(String code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override
    public String getCode() { return code; }
    @Override
    public int getStatus() { return status; }
    @Override
    public String getMessage() { return message; }
}
