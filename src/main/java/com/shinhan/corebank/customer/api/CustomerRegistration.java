package com.shinhan.corebank.customer.api;

// 다른 모듈에 인터넷뱅킹 고객 등록 기능을 공개한다.
public interface CustomerRegistration {

    RegisteredCustomer register(RegisterCustomerCommand command);
}
