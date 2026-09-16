package com.shinhan.corebank.signup.application.port.in;

import java.time.OffsetDateTime;

// 회원가입 완료 후 생성된 고객 식별정보를 반환한다.
public record CompleteSignupResult(Long customerId, String userId, OffsetDateTime joinedAt) {}
