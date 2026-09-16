package com.shinhan.corebank.account.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// accountPasswordAuthToken의 300초 TTL과 고객·계좌 조건부 소비를 검증한다.
class AccountPasswordAuthTokenRedisAdapterTest extends IntegrationTestSupport {

    private static final String KEY_PREFIX = "account:password:auth:";

    @Autowired
    private AccountPasswordAuthTokenRedisAdapter adapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String token;

    @AfterEach
    void cleanUp() {
        if (token != null) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    @Test
    @DisplayName("인증 토큰을 고객·계좌 payload와 함께 300초 동안 저장한다")
    void savesWithFiveMinuteTtl() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        AccountPasswordAuthTokenPayload payload = payload(1L, 101L);

        adapter.save(token, payload, Duration.ofMinutes(5));

        assertThat(redisTemplate.opsForValue().get(KEY_PREFIX + token))
                .contains("\"customerId\":1")
                .contains("\"accountId\":101");
        assertThat(redisTemplate.getExpire(KEY_PREFIX + token, TimeUnit.SECONDS))
                .isBetween(295L, 300L);
    }

    @Test
    @DisplayName("고객 또는 계좌가 다르면 토큰을 소진하지 않는다")
    void mismatchDoesNotConsumeToken() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        AccountPasswordAuthTokenPayload original = payload(1L, 101L);
        adapter.save(token, original, Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, payload(2L, 101L))).isFalse();
        assertThat(adapter.consumeIfMatches(token, payload(1L, 102L))).isFalse();
        assertThat(redisTemplate.hasKey(KEY_PREFIX + token)).isTrue();
    }

    @Test
    @DisplayName("일치하는 인증 토큰은 한 번만 소비한다")
    void consumesMatchingTokenOnce() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        AccountPasswordAuthTokenPayload payload = payload(1L, 101L);
        adapter.save(token, payload, Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, payload)).isTrue();
        assertThat(adapter.consumeIfMatches(token, payload)).isFalse();
    }

    private AccountPasswordAuthTokenPayload payload(Long customerId, Long accountId) {
        return new AccountPasswordAuthTokenPayload(customerId, accountId);
    }
}
