package com.shinhan.corebank.customer.api;

// 다른 모듈에 아이디 찾기용 고객 식별정보만 공개한다.
public record CustomerIdentityData(Long customerId, String userId) {}
