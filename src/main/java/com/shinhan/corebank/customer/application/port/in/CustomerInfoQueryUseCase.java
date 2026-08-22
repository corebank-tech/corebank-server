package com.shinhan.corebank.customer.application.port.in;

// 로그인 고객의 기본정보 조회 기능을 정의한다.
public interface CustomerInfoQueryUseCase {

    CustomerInfoResult getCustomerInfo(Long customerId);
}
