package com.shinhan.corebank;

import org.springframework.test.context.TestPropertySource;

// Redis 어댑터 테스트 전용. provider=redis로 띄워 Redis 어댑터 빈만 올린다. Redis 제거 2단계에서 함께 지운다 (#580).
@TestPropertySource(properties = "app.ephemeral-store.provider=redis")
public abstract class RedisEphemeralStoreTestSupport extends IntegrationTestSupport {}
