package com.shinhan.corebank.customer.application.port.in;

// 관리자가 대상 고객 계정을 조작할 때 넘기는 행위자·대상·요청 IP.
public record AdminCustomerOperationCommand(Long adminCustomerId, Long targetCustomerId, String requestIp) {}
