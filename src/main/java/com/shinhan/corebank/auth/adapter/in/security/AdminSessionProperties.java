package com.shinhan.corebank.auth.adapter.in.security;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 관리자 세션만 전역 timeout 보다 길게 둔다. 전역값을 늘리면 고객 세션까지 함께 늘어난다.
// 설정이 빠지면 관리자 첫 로그인에서 터지지 않고 기동 시점에 드러나도록 검증을 건다.
// 범위도 같은 이유로 기동에서 막는다. SessionLoginManager 는 이 값을 초 단위 int 로 깎아
// HttpSession.setMaxInactiveInterval 에 넘기는데, 0 이하는 만료되지 않는 세션이 되고
// Integer.MAX_VALUE 초를 넘으면 깎이는 과정에서 설정한 적 없는 값으로 바뀐다.
@Validated
@ConfigurationProperties(prefix = "app.security.admin")
public record AdminSessionProperties(
        @NotNull @DurationMin(seconds = 1) @DurationMax(seconds = Integer.MAX_VALUE) Duration sessionTimeout) {}
