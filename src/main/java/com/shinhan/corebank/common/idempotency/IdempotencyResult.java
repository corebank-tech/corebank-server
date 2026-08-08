package com.shinhan.corebank.common.idempotency;

public record IdempotencyResult(boolean replay, Short httpStatus, String responseSnapshot) {
    public static IdempotencyResult proceed() {
        return new IdempotencyResult(false, null, null);
    }

    public static IdempotencyResult replay(Short httpStatus, String responseSnapshot) {
        return new IdempotencyResult(true, httpStatus, responseSnapshot);
    }
}
