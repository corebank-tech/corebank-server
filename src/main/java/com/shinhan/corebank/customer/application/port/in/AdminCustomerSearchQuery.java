package com.shinhan.corebank.customer.application.port.in;

// 관리자 고객 검색 입력. 빈 문자열은 조건 없음으로 보고, 조건이 하나도 없으면 거부한다.
public record AdminCustomerSearchQuery(String userId, String userName, String email, Boolean accountLocked) {}
