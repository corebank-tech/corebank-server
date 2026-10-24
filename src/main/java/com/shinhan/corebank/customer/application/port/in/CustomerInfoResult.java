package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.time.OffsetDateTime;
import java.util.List;

// 마스킹된 로그인 고객의 기본정보 조회 결과다.
public record CustomerInfoResult(
        Long customerId,
        String userName,
        String userId,
        String birthDate,
        String phoneNumber,
        String email,
        OffsetDateTime joinedAt,
        CustomerRole role,
        List<AdminPermission> permissions) {}
