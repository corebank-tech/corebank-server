package com.shinhan.corebank.auth.adapter.in.web;

import java.time.OffsetDateTime;

// 로그인 성공 고객정보와 세션 만료시각 응답
public record LoginResponse(
        Long customerId,
        String userName,
        OffsetDateTime sessionExpiresAt
) {
}
