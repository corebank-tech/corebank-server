package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// 마이그레이션의 백필 UPDATE 문장을 그대로 읽어 실행한다. 2099-01-05(월)~01-13(화) 구간은 실제 휴일과 겹치지 않는다.
@DisplayName("가입 논리 영업일 백필 마이그레이션 테스트")
class SubscriptionTradeDateBackfillMigrationTest extends IntegrationTestSupport {

    private static final String MIGRATION =
            "db/migration/V202610201210__add_trade_date_to_product_subscription_and_account.sql";
    private static final long ID_BASE = 9_664_000_000L;
    private static final LocalDate TEST_HOLIDAY = LocalDate.of(2099, 1, 7); // 수
    private static final LocalDate TEST_HOLIDAY_AFTER_WEEKEND = LocalDate.of(2099, 1, 12); // 토·일 다음 월

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        for (LocalDate holiday : List.of(TEST_HOLIDAY, TEST_HOLIDAY_AFTER_WEEKEND)) {
            jdbcTemplate.update("DELETE FROM holiday WHERE holiday_date = ?", holiday);
            jdbcTemplate.update(
                    "INSERT INTO holiday (holiday_date, holiday_name, created_at, updated_at) VALUES (?, '백필 테스트', NOW(6), NOW(6))",
                    holiday);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM product_subscription WHERE subscription_id BETWEEN ? AND ?", ID_BASE, ID_BASE + 99);
        jdbcTemplate.update(
                "DELETE FROM holiday WHERE holiday_date IN (?, ?)", TEST_HOLIDAY, TEST_HOLIDAY_AFTER_WEEKEND);
    }

    @Test
    @DisplayName("영업일은 그대로, 주말은 월요일로, 공휴일은 다음 영업일로, 연휴 앞 주말은 연휴 뒤 첫 영업일로 채운다")
    void backfillRollsSubscribedAtToBusinessDay() throws Exception {
        insert(1, LocalDateTime.of(2099, 1, 6, 23, 59)); // 화 → 화
        insert(2, LocalDateTime.of(2099, 1, 3, 10, 0)); // 토 → 월
        insert(3, LocalDateTime.of(2099, 1, 7, 9, 0)); // 수(휴일) → 목
        insert(4, LocalDateTime.of(2099, 1, 4, 9, 0)); // 일 → 월
        insert(6, LocalDateTime.of(2099, 1, 10, 9, 0)); // 토(다음 일, 월이 휴일) → 화

        runBackfill();

        assertThat(tradeDateOf(1)).isEqualTo(LocalDate.of(2099, 1, 6));
        assertThat(tradeDateOf(2)).isEqualTo(LocalDate.of(2099, 1, 5));
        assertThat(tradeDateOf(3)).isEqualTo(LocalDate.of(2099, 1, 8));
        assertThat(tradeDateOf(4)).isEqualTo(LocalDate.of(2099, 1, 5));
        assertThat(tradeDateOf(6)).isEqualTo(LocalDate.of(2099, 1, 13));
    }

    @Test
    @DisplayName("이미 값이 있는 행은 덮어쓰지 않는다")
    void backfillKeepsExistingValue() throws Exception {
        insert(5, LocalDateTime.of(2099, 1, 6, 10, 0));
        jdbcTemplate.update(
                "UPDATE product_subscription SET subscription_trade_date = ? WHERE subscription_id = ?",
                LocalDate.of(2099, 1, 9),
                ID_BASE + 5);

        runBackfill();

        assertThat(tradeDateOf(5)).isEqualTo(LocalDate.of(2099, 1, 9));
    }

    private void runBackfill() throws Exception {
        String sql =
                new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String backfill = sql.substring(sql.indexOf("WITH RECURSIVE")).trim();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(backfill.endsWith(";") ? backfill.substring(0, backfill.length() - 1) : backfill);
        }
    }

    // 외래키 검사를 이 연결에서만 끄고 백필 대상 컬럼만 의미 있는 최소 행을 넣는다.
    private void insert(int offset, LocalDateTime subscribedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO product_subscription (
                        subscription_id, customer_id, product_id, withdrawal_account_id, subscription_amount,
                        term_months, base_rate, preferential_rate, applied_rate, maturity_handling, status, subscribed_at
                    ) VALUES (?, 1, 1, 1, 1000, 12, 1.00, 0.00, 1.00, 'TRANSFER', 'SUCCESS', ?)
                    """)) {
                statement.setLong(1, ID_BASE + offset);
                statement.setObject(2, subscribedAt);
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private LocalDate tradeDateOf(int offset) {
        return jdbcTemplate.queryForObject(
                "SELECT subscription_trade_date FROM product_subscription WHERE subscription_id = ?",
                LocalDate.class,
                ID_BASE + offset);
    }
}
