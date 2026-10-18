package com.shinhan.corebank.customer.api;

// 계정 구분. 관리자도 customer 행이라 로그인 경로와 비밀번호 정책을 고객과 공유한다.
// common_code 의 CUSTOMER_ROLE 그룹과 값이 일치해야 한다 (REQ-CMN-023).
public enum CustomerRole {
    CUSTOMER,
    ADMIN
}
