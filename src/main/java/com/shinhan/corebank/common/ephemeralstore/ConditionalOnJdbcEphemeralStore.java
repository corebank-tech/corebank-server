package com.shinhan.corebank.common.ephemeralstore;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

// app.ephemeral-store.provider=jdbc(기본값)일 때만 MySQL 토큰·잠금·이력 어댑터를 띄운다 (#580).
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "app.ephemeral-store", name = "provider", havingValue = "jdbc", matchIfMissing = true)
public @interface ConditionalOnJdbcEphemeralStore {}
