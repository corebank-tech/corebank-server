package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.common.audit.AccessReason;

// 관리자가 대상 고객 계정을 조작할 때 넘기는 행위자·대상·요청 IP·사유(PH-89).
public record AdminCustomerOperationCommand(
        Long adminCustomerId, Long targetCustomerId, String requestIp, AccessReason accessReason) {}
