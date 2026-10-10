package com.shinhan.corebank.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.application.port.out.TermsView;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 상품 약관 열람 이력의 30분 인정·만료·재열람 갱신을 MySQL에서 검증한다.
class TermsViewHistoryPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 958_004L;
    private static final Long TERMS_ID = 958_301L;

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
    @DisplayName("record 후 find하면 같은 열람 이력을 돌려주고 30분 동안 인정한다")
    void record_thenFind() {
        TermsView recorded = adapter.record(CUSTOMER_ID, TERMS_ID);

        assertThat(Duration.between(recorded.viewedAt(), recorded.viewExpiresAt()))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(adapter.find(CUSTOMER_ID, TERMS_ID)).contains(recorded);
    }

    @Test
    @DisplayName("기록된 적 없는 조합이면 빈 값을 돌려준다")
    void find_withoutRecord_returnsEmpty() {
        assertThat(adapter.find(CUSTOMER_ID, TERMS_ID)).isEmpty();
    }

    @Test
    @DisplayName("만료된 열람 이력은 인정하지 않는다")
    void find_afterExpiry_returnsEmpty() {
        adapter.record(CUSTOMER_ID, TERMS_ID);
        jdbcTemplate.update(
                "UPDATE terms_view_history SET expires_at = ? WHERE customer_id = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                CUSTOMER_ID);

        assertThat(adapter.find(CUSTOMER_ID, TERMS_ID)).isEmpty();
    }

    @Test
    @DisplayName("다시 열람하면 같은 행의 열람·만료 시각을 새로 갱신한다")
    void record_again_updatesSameRow() {
        adapter.record(CUSTOMER_ID, TERMS_ID);
        jdbcTemplate.update(
                "UPDATE terms_view_history SET expires_at = ? WHERE customer_id = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                CUSTOMER_ID);

        TermsView renewed = adapter.record(CUSTOMER_ID, TERMS_ID);

        assertThat(adapter.find(CUSTOMER_ID, TERMS_ID)).contains(renewed);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM terms_view_history WHERE customer_id = ?", Integer.class, CUSTOMER_ID);
        assertThat(rows).isOne();
    }
}
