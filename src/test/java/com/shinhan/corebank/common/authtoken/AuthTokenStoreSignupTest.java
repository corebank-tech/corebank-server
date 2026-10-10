package com.shinhan.corebank.common.authtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.authtoken.AuthTokenStore.Source;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

// 회원가입 토큰 전환(여러 토큰 소비 + 임시 토큰 발급)과 임시 토큰 선점·완료·복구를 MySQL에서 검증한다.
class AuthTokenStoreSignupTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final AuthTokenPurpose TEMP = AuthTokenPurpose.TEMP_SIGNUP;

    @Autowired
    AuthTokenStore store;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        tokens.forEach(token ->
                jdbcTemplate.update("DELETE FROM auth_token WHERE token_hash = ?", AuthTokenStore.hash(token)));
    }

    @Test
    @DisplayName("원본 토큰을 모두 소비하고 임시 토큰을 한 번에 발급한다")
    void transition_consumesSourcesAndIssuesDestination() {
        List<Source> sources = savedSources(
                AuthTokenPurpose.TERMS_AUTH,
                AuthTokenPurpose.ACCOUNT_AUTH,
                AuthTokenPurpose.USER_ID_CHECK,
                AuthTokenPurpose.EMAIL_VERIFICATION);
        String temp = newToken();

        assertThat(store.transition(sources, TEMP, temp, "snapshot", TTL)).isTrue();

        sources.forEach(source -> assertThat(store.find(source.purpose(), source.token(), String.class))
                .isEmpty());
        assertThat(store.find(TEMP, temp, String.class)).contains("snapshot");
    }

    @Test
    @DisplayName("원본 토큰이 하나라도 없으면 아무것도 소비하지 않고 임시 토큰도 만들지 않는다")
    void transition_withMissingSource_leavesEverythingUntouched() {
        List<Source> sources =
                new ArrayList<>(savedSources(AuthTokenPurpose.TERMS_AUTH, AuthTokenPurpose.ACCOUNT_AUTH));
        sources.add(new Source(AuthTokenPurpose.USER_ID_CHECK, newToken()));
        String temp = newToken();

        assertThat(store.transition(sources, TEMP, temp, "snapshot", TTL)).isFalse();

        assertThat(store.find(sources.get(0).purpose(), sources.get(0).token(), String.class))
                .isPresent();
        assertThat(store.find(sources.get(1).purpose(), sources.get(1).token(), String.class))
                .isPresent();
        assertThat(store.find(TEMP, temp, String.class)).isEmpty();
    }

    @Test
    @DisplayName("만료된 원본 토큰이 섞여 있으면 전환하지 않는다")
    void transition_withExpiredSource_returnsFalse() {
        List<Source> sources = savedSources(AuthTokenPurpose.TERMS_AUTH, AuthTokenPurpose.ACCOUNT_AUTH);
        expire(sources.get(1).token());

        assertThat(store.transition(sources, TEMP, newToken(), "snapshot", TTL)).isFalse();
        assertThat(store.find(sources.get(0).purpose(), sources.get(0).token(), String.class))
                .isPresent();
    }

    @Test
    @DisplayName("발급할 임시 토큰이 이미 있으면 예외를 던지고 원본 토큰은 그대로 둔다")
    void transition_withDestinationCollision_throwsAndKeepsSources() {
        List<Source> sources = savedSources(AuthTokenPurpose.TERMS_AUTH);
        String temp = newToken();
        store.save(TEMP, temp, null, "existing", TTL);

        assertThatThrownBy(() -> store.transition(sources, TEMP, temp, "snapshot", TTL))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.find(sources.get(0).purpose(), sources.get(0).token(), String.class))
                .isPresent();
    }

    @Test
    @DisplayName("같은 임시 토큰을 동시에 회전하면 정확히 1건만 성공한다")
    void transition_concurrentRotation_onlyOneSucceeds() throws Exception {
        String current = newToken();
        store.save(TEMP, current, null, "snapshot", TTL);
        int workers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                String next = newToken();
                results.add(executor.submit(() -> {
                    start.await();
                    return store.transition(List.of(new Source(TEMP, current)), TEMP, next, "rotated", TTL);
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
    @DisplayName("선점은 한 번만 되고, 선점된 토큰은 조회·소비되지 않으며, 완료하면 다시 쓸 수 없다")
    void claim_onceAndCompletePermanently() {
        String temp = newToken();
        store.save(TEMP, temp, null, "snapshot", TTL);
        String claimId = UUID.randomUUID().toString();

        assertThat(store.claim(TEMP, temp, claimId, String.class)).contains("snapshot");
        assertThat(store.claim(TEMP, temp, UUID.randomUUID().toString(), String.class))
                .isEmpty();
        assertThat(store.find(TEMP, temp, String.class)).isEmpty();
        assertThat(store.consume(TEMP, temp, String.class)).isEmpty();

        assertThat(store.completeClaim(TEMP, temp, "other-claim")).isFalse();
        assertThat(store.completeClaim(TEMP, temp, claimId)).isTrue();
        assertThat(store.releaseClaim(TEMP, temp, claimId)).isFalse();
        assertThat(store.find(TEMP, temp, String.class)).isEmpty();
    }

    @Test
    @DisplayName("선점을 복구하면 원래 만료 시각 그대로 다시 쓸 수 있고, 남의 claimId로는 복구하지 못한다")
    void releaseClaim_restoresWithOriginalExpiry() {
        String temp = newToken();
        store.save(TEMP, temp, null, "snapshot", TTL);
        LocalDateTime originalExpiry = expiresAt(temp);
        String claimId = UUID.randomUUID().toString();
        store.claim(TEMP, temp, claimId, String.class);

        assertThat(store.releaseClaim(TEMP, temp, "other-claim")).isFalse();
        assertThat(store.releaseClaim(TEMP, temp, claimId)).isTrue();

        assertThat(store.find(TEMP, temp, String.class)).contains("snapshot");
        assertThat(expiresAt(temp)).isEqualTo(originalExpiry);
    }

    @Test
    @DisplayName("같은 임시 토큰을 동시에 선점하면 한 요청만 성공한다")
    void claim_concurrently_onlyOneOwner() throws Exception {
        String temp = newToken();
        store.save(TEMP, temp, null, "snapshot", TTL);
        int workers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<String>>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return store.claim(TEMP, temp, UUID.randomUUID().toString(), String.class);
                }));
            }
            start.countDown();

            int owners = 0;
            for (Future<Optional<String>> result : results) {
                if (result.get(30, TimeUnit.SECONDS).isPresent()) {
                    owners++;
                }
            }
            assertThat(owners).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Source> savedSources(AuthTokenPurpose... purposes) {
        List<Source> sources = new ArrayList<>();
        for (AuthTokenPurpose purpose : purposes) {
            String token = newToken();
            store.save(purpose, token, null, purpose.name(), TTL);
            sources.add(new Source(purpose, token));
        }
        return sources;
    }

    private void expire(String token) {
        jdbcTemplate.update(
                "UPDATE auth_token SET expires_at = ? WHERE token_hash = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                AuthTokenStore.hash(token));
    }

    private LocalDateTime expiresAt(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT expires_at FROM auth_token WHERE token_hash = ?",
                LocalDateTime.class,
                AuthTokenStore.hash(token));
    }

    private String newToken() {
        String token = "TEST_" + UUID.randomUUID();
        tokens.add(token);
        return token;
    }
}
