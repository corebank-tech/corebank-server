package com.shinhan.corebank.customer.api;

// 관리자가 열 수 있는 문의 목록. admin_permission 에 행 하나로 들어간다.
// DB CHECK(ck_admin_permission) · common_code 의 ADMIN_PERMISSION 그룹과 값이 모두 일치해야 한다.
// 권한을 늘리면 세 곳을 함께 바꾼다.
public enum AdminPermission {
    GL_READ,
    GL_WRITE,
    CUSTOMER_READ,
    CUSTOMER_WRITE,
    AUDIT_READ,
    PAYMENT_STOP_READ,
    PAYMENT_STOP_WRITE,
    // 보류를 줄이는 판정(수용, 일부 지급정지)의 승인. 작성자와 다른 관리자가 한다
    PAYMENT_STOP_APPROVE
}
