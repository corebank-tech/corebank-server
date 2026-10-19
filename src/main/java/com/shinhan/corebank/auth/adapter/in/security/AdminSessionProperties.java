package com.shinhan.corebank.auth.adapter.in.security;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 관리자 세션만 전역 timeout 보다 길게 둔다. 전역값을 늘리면 고객 세션까지 함께 늘어난다.
// 설정이 빠지면 관리자 첫 로그인에서 터지지 않고 기동 시점에 드러나도록 검증을 건다.
@Validated
@ConfigurationProperties(prefix = "app.security.admin")
public record AdminSessionProperties(@NotNull Duration sessionTimeout) {}
