package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.domain.model.CustomerStatus;

// 관리자 계정 상태 변경 후 대상 고객의 상태.
public record AdminStatusChangeResult(Long customerId, CustomerStatus status) {}
