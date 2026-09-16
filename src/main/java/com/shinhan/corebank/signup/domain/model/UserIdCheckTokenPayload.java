package com.shinhan.corebank.signup.domain.model;

import java.time.LocalDateTime;

// 중복확인을 마친 아이디와 확인 시각을 Redis에 보관한다.
public record UserIdCheckTokenPayload(String userId, LocalDateTime checkedAt) {}
