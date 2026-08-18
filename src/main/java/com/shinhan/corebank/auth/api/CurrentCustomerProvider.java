package com.shinhan.corebank.auth.api;

//타 모듈이 로그인한 고객 정보를 조회할 수 있게 하는 공개 인터페이스
public interface CurrentCustomerProvider {
    AuthenticatedCustomer getCurrentCustomer();
    Long getCurrentCustomerId();
}