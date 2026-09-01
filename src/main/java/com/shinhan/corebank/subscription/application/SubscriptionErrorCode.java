package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum SubscriptionErrorCode implements ErrorCode {
    SUBSCRIPTION_NOT_FOUND("PRD0203", 404, "가입 내역을 찾을 수 없습니다."),
    SUBSCRIPTION_ACCOUNT_NOT_FOUND("PRD9003", 500, "가입 건에 연결된 계좌 정보를 찾을 수 없습니다."),
    ALREADY_SUBSCRIBED("PRD0301", 409, "이미 가입한 상품입니다."),
    PRODUCT_LOCK_TARGET_NOT_FOUND("PRD9002", 500, "가입 처리 중 상품 정보를 확인할 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

    SubscriptionErrorCode(String code, int status, String message) {
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
