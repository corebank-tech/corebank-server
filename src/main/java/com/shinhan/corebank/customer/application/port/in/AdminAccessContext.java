package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.common.audit.AccessReason;

// 관리자가 고객 개인정보를 열 때의 행위자·요청 IP·사유. 조회 감사의 Who·Where·Why가 된다(PH-89).
public record AdminAccessContext(Long adminCustomerId, String requestIp, AccessReason accessReason) {}
