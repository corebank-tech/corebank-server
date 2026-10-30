package com.shinhan.corebank.common.authtoken;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthTokenCleanupSchedulerTest extends IntegrationTestSupport {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.TERMS_AUTH;

    @Autowired
    AuthTokenCleanupScheduler scheduler;

    @Autowired
    AuthTokenStore store;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    private final String expiredToken = "TEST_" + UUID.randomUUID();
    private final String consumedToken = "TEST_" + UUID.randomUUID();
    private final String freshToken = "TEST_" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        List.of(expiredToken, consumedToken, freshToken)
                .forEach(token ->
                        jdbcTemplate.update("DELETE FROM auth_token WHERE token_hash = ?", AuthTokenStore.hash(token)));
    }

    @Test
    @DisplayName("만료 시각이 지난 토큰만 지우고, 소비됐지만 만료 전인 토큰과 유효한 토큰은 남긴다")
    void cleanupExpired_deletesOnlyExpiredTokens() {
        List.of(expiredToken, consumedToken, freshToken)
                .forEach(token -> store.save(PURPOSE, token, null, "payload", Duration.ofMinutes(30)));
        jdbcTemplate.update(
                "UPDATE auth_token SET expires_at = ? WHERE token_hash = ?",
                LocalDateTime.now(clock).minusMinutes(1),
                AuthTokenStore.hash(expiredToken));
        store.consume(PURPOSE, consumedToken, String.class);

        scheduler.cleanupExpired();

        assertThat(rowCount(expiredToken)).isZero();
        assertThat(rowCount(consumedToken)).isOne();
        assertThat(rowCount(freshToken)).isOne();
    }

    private Integer rowCount(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_token WHERE token_hash = ?", Integer.class, AuthTokenStore.hash(token));
    }
}
