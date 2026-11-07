package com.shinhan.corebank.notification.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림을 발생시킨 업무 구분. notification.type 컬럼 값과 같다(api_conventions.md §5-9). */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    TRANSFER("이체"), // 즉시 · 타행이체 결과
    SCHEDULED_TRANSFER("예약이체"), // 예약이체 결과
    AUTO_TRANSFER("자동이체"), // 자동이체 회차 결과
    PRODUCT_SUBSCRIPTION("상품가입"); // 상품가입 결과

    private final String displayName;
}
