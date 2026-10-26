package com.shinhan.corebank.notification.api;

/** 알림을 발생시킨 업무 구분. notification.type 컬럼 값과 같다(api_conventions.md §5-9). */
public enum NotificationType {
    TRANSFER, // 즉시 · 타행이체 결과
    SCHEDULED_TRANSFER, // 예약이체 결과
    AUTO_TRANSFER, // 자동이체 회차 결과
    PRODUCT_SUBSCRIPTION // 상품가입 결과
}
