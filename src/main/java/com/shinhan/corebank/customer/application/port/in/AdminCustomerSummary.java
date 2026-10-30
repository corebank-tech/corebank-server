package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import java.time.OffsetDateTime;

// 관리자 고객 목록 한 줄. 개인정보는 가려져 있고 생년월일·연락처는 목록에 싣지 않는다.
public record AdminCustomerSummary(
        Long customerId,
        String userId,
        String userName,
        String email,
        int loginFailureCount,
        boolean accountLocked,
        CustomerStatus status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime joinedAt) {}
