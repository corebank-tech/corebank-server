package com.shinhan.corebank.common.ephemeralstore;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

// app.ephemeral-store.provider=redis일 때만 Redis 어댑터를 띄운다. PH-101-② 판정 통과 뒤 Redis 어댑터와 함께 지운다 (#580).
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "app.ephemeral-store", name = "provider", havingValue = "redis")
public @interface ConditionalOnRedisEphemeralStore {}
