package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TermsViewHistoryCleanupSchedulerTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 958_005L;
    private static final Long EXPIRED_TERMS_ID = 958_302L;
    private static final Long FRESH_TERMS_ID = 958_303L;

    @Autowired
    TermsViewHistoryCleanupScheduler scheduler;

    @Autowired
    TermsViewHistoryPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM terms_view_history WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("만료 시각이 지난 열람 이력만 지우고 유효한 이력은 남긴다")
    void cleanupExpired_deletesOnlyExpiredHistory() {
        adapter.record(CUSTOMER_ID, EXPIRED_TERMS_ID);
        adapter.record(CUSTOMER_ID, FRESH_TERMS_ID);
        jdbcTemplate.update(
                "UPDATE terms_view_history SET expires_at = ? WHERE customer_id = ? AND terms_id = ?",
                LocalDateTime.now(clock).minusMinutes(1),
                CUSTOMER_ID,
                EXPIRED_TERMS_ID);

        scheduler.cleanupExpired();

        assertThat(rowCount(EXPIRED_TERMS_ID)).isZero();
        assertThat(rowCount(FRESH_TERMS_ID)).isOne();
    }

    private Integer rowCount(Long termsId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM terms_view_history WHERE customer_id = ? AND terms_id = ?",
                Integer.class,
                CUSTOMER_ID,
                termsId);
    }
}
