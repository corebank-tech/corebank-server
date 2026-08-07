package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("PRD0201", 404, "상품을 찾을 수 없습니다.");
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
