package com.shinhan.corebank.customer.api;

// 신규 등록된 인터넷뱅킹 고객의 식별정보를 반환한다.
public record RegisteredCustomer(Long customerId, String userId) {}
