package com.shinhan.corebank.customer.application.port.in;

public interface LoginStatusQueryUseCase {
    LoginStatusResult getLoginStatus(Long customerId, String currentIp);
}
