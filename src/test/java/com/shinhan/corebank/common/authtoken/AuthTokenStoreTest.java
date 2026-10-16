package com.shinhan.corebank.common.authtoken;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 MySQL에서 토큰 저장·만료·일회성 소비와 호출자 트랜잭션 참여를 검증한다.
class AuthTokenStoreTest extends IntegrationTestSupport {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.OTP_AUTH;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    AuthTokenStore store;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        tokens.forEach(token ->
                jdbcTemplate.update("DELETE FROM auth_token WHERE token_hash = ?", AuthTokenStore.hash(token)));
    }

    @Test
    @DisplayName("저장한 토큰을 조회하면 payload를 돌려주고, DB에는 원문 대신 해시만 남는다")
    void save_thenFind_returnsPayloadAndStoresHashOnly() {
        String token = newToken();
        SamplePayload payload = new SamplePayload("REQ-1", 7L);

        store.save(PURPOSE, token, 7L, payload, TTL);

        assertThat(store.find(PURPOSE, token, SamplePayload.class)).contains(payload);
        Integer rawTokenRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_token WHERE token_hash = ?", Integer.class, token);
        assertThat(rawTokenRows).isZero();
    }

    @Test
    @DisplayName("같은 토큰도 용도가 다르면 찾지 못한다")
    void find_withOtherPurpose_returnsEmpty() {
        String token = newToken();
        store.save(PURPOSE, token, 7L, new SamplePayload("REQ-1", 7L), TTL);

        assertThat(store.find(AuthTokenPurpose.ACCOUNT_PASSWORD_AUTH, token, SamplePayload.class))
                .isEmpty();
    }

    @Test
    @DisplayName("토큰은 한 번만 소비되고, 소비된 토큰은 조회되지 않는다")
    void consume_onlyOnce() {
        String token = newToken();
        SamplePayload payload = new SamplePayload("REQ-1", 7L);
        store.save(PURPOSE, token, 7L, payload, TTL);

        assertThat(store.consume(PURPOSE, token, SamplePayload.class)).contains(payload);
        assertThat(store.consume(PURPOSE, token, SamplePayload.class)).isEmpty();
        assertThat(store.find(PURPOSE, token, SamplePayload.class)).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 정리 배치가 돌기 전에도 조회·소비되지 않는다")
    void expiredToken_isRejectedWithoutCleanup() {
        String token = newToken();
        SamplePayload payload = new SamplePayload("REQ-1", 7L);
        store.save(PURPOSE, token, 7L, payload, TTL);
        jdbcTemplate.update(
                "UPDATE auth_token SET expires_at = ? WHERE token_hash = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                AuthTokenStore.hash(token));

        assertThat(store.find(PURPOSE, token, SamplePayload.class)).isEmpty();
        assertThat(store.consume(PURPOSE, token, SamplePayload.class)).isEmpty();
        assertThat(store.consumeIfMatches(PURPOSE, token, payload)).isFalse();
        assertThat(store.consumeIfCustomerMatches(PURPOSE, token, 7L)).isFalse();
    }

    @Test
    @DisplayName("payload가 다르면 소비하지 않고, 같을 때만 소비한다")
    void consumeIfMatches_onlyWhenPayloadEquals() {
        String token = newToken();
        store.save(PURPOSE, token, 7L, new SamplePayload("REQ-1", 7L), TTL);

        assertThat(store.consumeIfMatches(PURPOSE, token, new SamplePayload("req-1", 7L)))
                .isFalse();
        assertThat(store.consumeIfMatches(PURPOSE, token, new SamplePayload("REQ-1", 8L)))
                .isFalse();
        assertThat(store.consumeIfMatches(PURPOSE, token, new SamplePayload("REQ-1", 7L)))
                .isTrue();
        assertThat(store.consumeIfMatches(PURPOSE, token, new SamplePayload("REQ-1", 7L)))
                .isFalse();
    }

    @Test
    @DisplayName("고객이 다르면 소비하지 않고, 같을 때만 소비한다")
    void consumeIfCustomerMatches_onlyWhenCustomerEquals() {
        String token = newToken();
        store.save(PURPOSE, token, 7L, new SamplePayload("REQ-1", 7L), TTL);

        assertThat(store.consumeIfCustomerMatches(PURPOSE, token, 8L)).isFalse();
        assertThat(store.consumeIfCustomerMatches(PURPOSE, token, 7L)).isTrue();
        assertThat(store.consumeIfCustomerMatches(PURPOSE, token, 7L)).isFalse();
    }

    @Test
    @DisplayName("같은 토큰을 동시에 10번 소비하면 정확히 1건만 성공한다")
    void consume_concurrently_onlyOneSucceeds() throws Exception {
        String token = newToken();
        store.save(PURPOSE, token, 7L, new SamplePayload("REQ-1", 7L), TTL);
        int workers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return store.consume(PURPOSE, token, SamplePayload.class).isPresent();
                }));
            }
            start.countDown();

            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백되면 소비도 롤백돼 같은 토큰을 다시 쓸 수 있다")
    void consume_rolledBackWithCallerTransaction() {
        String token = newToken();
        SamplePayload payload = new SamplePayload("REQ-1", 7L);
        store.save(PURPOSE, token, 7L, payload, TTL);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(store.consumeIfMatches(PURPOSE, token, payload)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(store.consumeIfMatches(PURPOSE, token, payload)).isTrue();
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백되면 저장도 롤백된다")
    void save_rolledBackWithCallerTransaction() {
        String token = newToken();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            store.save(PURPOSE, token, 7L, new SamplePayload("REQ-1", 7L), TTL);
            status.setRollbackOnly();
        });

        assertThat(store.find(PURPOSE, token, SamplePayload.class)).isEmpty();
    }

    private String newToken() {
        String token = "TEST_" + UUID.randomUUID();
        tokens.add(token);
        return token;
    }

    record SamplePayload(String requestId, Long customerId) {}
}
