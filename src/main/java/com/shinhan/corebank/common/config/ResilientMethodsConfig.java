package com.shinhan.corebank.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

// @Retryable 을 켠다. 기본 순서라 @Async 안쪽 · 트랜잭션 바깥에서 돌아, 시도마다 새 트랜잭션으로 다시 부른다
@Configuration
@EnableResilientMethods
public class ResilientMethodsConfig {}
