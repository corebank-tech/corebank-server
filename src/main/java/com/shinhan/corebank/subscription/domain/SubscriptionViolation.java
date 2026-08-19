package com.shinhan.corebank.subscription.domain;

public record SubscriptionViolation(String field, String code, String reason) {

    public static SubscriptionViolation of(String field, SubscriptionViolationCode violationCode) {
        return new SubscriptionViolation(field, violationCode.getCode(), violationCode.getMessage());
    }

    public static SubscriptionViolation of(String field, SubscriptionViolationCode violationCode, String detail) {
        return new SubscriptionViolation(
                field, violationCode.getCode(), violationCode.getMessage() + " (" + detail + ")");
    }
}
