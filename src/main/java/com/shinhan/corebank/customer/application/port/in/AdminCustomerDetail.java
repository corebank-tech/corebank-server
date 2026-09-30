package com.shinhan.corebank.customer.application.port.in;

import java.time.OffsetDateTime;

// 관리자 고객 상세. 모든 개인정보는 가려져 있다.
public record AdminCustomerDetail(
        Long customerId,
        String userId,
        String userName,
        String birthDate,
        String email,
        String phoneNumber,
        int loginFailureCount,
        boolean accountLocked,
        OffsetDateTime lastLoginAt,
        OffsetDateTime joinedAt) {}
