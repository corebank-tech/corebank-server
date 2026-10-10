package com.shinhan.corebank.common.ephemeralstore;

import org.springframework.boot.data.redis.health.DataRedisHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

// 자동 등록(management.health.redis.enabled)은 꺼 두고 provider=redis일 때만 Redis를 헬스체크에 넣는다 (#580).
@Configuration(proxyBeanMethods = false)
@ConditionalOnRedisEphemeralStore
public class RedisEphemeralStoreHealthConfiguration {

    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        return new DataRedisHealthIndicator(redisConnectionFactory);
    }
}
