package com.shinhan.corebank.account.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

    @Test
    @DisplayName("동일 고객이면 계좌와 무관하게 토큰을 한 번만 소비한다")
    void consumesTokenByCustomerOnce() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(1L, 101L), Duration.ofMinutes(5));

        assertThat(adapter.consumeIfCustomerMatches(token, 1L)).isTrue();
        assertThat(adapter.consumeIfCustomerMatches(token, 1L)).isFalse();
    }

    @Test
    @DisplayName("고객이 다르면 토큰을 삭제하지 않는다")
    void customerMismatchDoesNotConsumeToken() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(1L, 101L), Duration.ofMinutes(5));

        assertThat(adapter.consumeIfCustomerMatches(token, 2L)).isFalse();
        assertThat(redisTemplate.hasKey(KEY_PREFIX + token)).isTrue();
        assertThat(adapter.consumeIfCustomerMatches(token, 1L)).isTrue();
    }

    @Test
    @DisplayName("Long 범위의 고객 ID도 손실 없이 비교한다")
    void consumesTokenForMaximumLongCustomerId() {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(Long.MAX_VALUE, 101L), Duration.ofMinutes(5));

        assertThat(adapter.consumeIfCustomerMatches(token, Long.MAX_VALUE)).isTrue();
    }

    @Test
    @DisplayName("만료된 고객 기준 인증 토큰은 소비할 수 없다")
    void rejectsExpiredCustomerToken() throws Exception {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(1L, 101L), Duration.ofMillis(50));

        TimeUnit.MILLISECONDS.sleep(100);

        assertThat(adapter.consumeIfCustomerMatches(token, 1L)).isFalse();
    }

    @Test
    @DisplayName("같은 토큰을 동시에 소비하면 하나의 요청만 성공한다")
    void onlyOneConcurrentCustomerConsumptionSucceeds() throws Exception {
        token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(1L, 101L), Duration.ofMinutes(5));
        int requests = 8;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requests)) {
            var futures = java.util.stream.IntStream.range(0, requests)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return adapter.consumeIfCustomerMatches(token, 1L);
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successCount = 0;
            for (var future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertThat(successCount).isOne();
        }
    }

    private AccountPasswordAuthTokenPayload payload(Long customerId, Long accountId) {
        return new AccountPasswordAuthTokenPayload(customerId, accountId);
    }
}
