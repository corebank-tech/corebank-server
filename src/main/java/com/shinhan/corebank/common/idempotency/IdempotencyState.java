package com.shinhan.corebank.common.idempotency;

public enum IdempotencyState {
    PROCESSING, // 처리 중 → 재요청 시 CMN0301 (409)
    COMPLETED // 완료   → 재요청 시 저장된 응답을 그대로 반환
}
