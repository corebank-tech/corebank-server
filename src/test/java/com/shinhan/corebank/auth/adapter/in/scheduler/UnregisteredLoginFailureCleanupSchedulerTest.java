package com.shinhan.corebank.auth.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.adapter.out.security.LoginIdBlindIndex;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UnregisteredLoginFailureCleanupSchedulerTest extends IntegrationTestSupport {

    // 다른 테스트의 ph08ghost 대역과 겹치지 않는 아이디
    private static final String EXPIRED_USER_ID = "ph08cleanup01";
    private static final String ACTIVE_USER_ID = "ph08cleanup02";

    @Autowired
    UnregisteredLoginFailureCleanupScheduler scheduler;

    @Autowired
    LoginIdBlindIndex loginIdBlindIndex;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM unregistered_login_failure WHERE user_id_hash IN (?, ?)",
                loginIdBlindIndex.hash(EXPIRED_USER_ID),
                loginIdBlindIndex.hash(ACTIVE_USER_ID));
    }

    @Test
    @DisplayName("만료 시각이 지난 실패 기록만 지우고 아직 유효한 기록은 남긴다")
    void cleanupExpired_deletesOnlyExpiredRows() {
        // Given
        final LocalDateTime now = LocalDateTime.now(clock);
        insertFailure(EXPIRED_USER_ID, now.minusDays(31), now.minusDays(1));
        insertFailure(ACTIVE_USER_ID, now.minusDays(1), now.plusDays(29));

        // When
        scheduler.cleanupExpired();

        // Then
        assertThat(rowCount(EXPIRED_USER_ID)).isZero();
        assertThat(rowCount(ACTIVE_USER_ID)).isOne();
    }

    private void insertFailure(String userId, LocalDateTime lastFailedAt, LocalDateTime expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO unregistered_login_failure (user_id_hash, failure_count, last_failed_at, expires_at)
                VALUES (?, 5, ?, ?)
                """,
                loginIdBlindIndex.hash(userId),
                lastFailedAt,
                expiresAt);
    }

    private Integer rowCount(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM unregistered_login_failure WHERE user_id_hash = ?",
                Integer.class,
                loginIdBlindIndex.hash(userId));
    }
}
