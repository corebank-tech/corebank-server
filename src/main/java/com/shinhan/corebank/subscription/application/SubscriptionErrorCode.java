package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.common.exception.ErrorCode;

public enum SubscriptionErrorCode implements ErrorCode {
    SUBSCRIPTION_NOT_FOUND("PRD0203", 404, "가입 내역을 찾을 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

    SubscriptionErrorCode(String code, int status, String message) {
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
