package com.shinhan.corebank.customer.application.port.out;

// 관리자 고객 검색 조건. null이면 그 조건을 걸지 않는다.
// userId·userName은 앞부분 일치, email은 정확 일치, accountLocked는 값 일치다.
public record CustomerSearchCondition(String userId, String userName, String email, Boolean accountLocked) {}
