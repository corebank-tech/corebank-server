package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

// 배포 QA 프로필이 상태별 고객·계좌 시드를 멱등하게 적재하는지 검증한다.
@ActiveProfiles({"test", "qa-seed"})
class QaSeedDataIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DemoDataLoader demoDataLoader;

    @Test
    @DisplayName("QA 시드를 재실행해도 세 고객과 열한 계좌의 상태 조합이 유지된다")
    void loadsQaSeedIdempotently() {
        assertThat(demoDataLoader).isNotNull();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                """
                UPDATE customer
                SET password_hash = 'qa-changed-password-hash',
                    user_name = 'QA 변경 이름',
                    login_failure_count = 5,
                    account_locked = TRUE
                WHERE user_id = 'honggildong'
                """);
        jdbc.update(
                """
                UPDATE account
                SET balance = 1,
                    password_failure_count = 5,
                    password_locked = TRUE,
                    alias = 'QA 변경 별칭'
                WHERE account_number = '088100000010'
                """);

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/seed/local-demo-data.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);

        assertThat(count(jdbc, "SELECT COUNT(*) FROM customer WHERE user_id IN ('honggildong','kimminji','leeseojun')"))
                .isEqualTo(3);
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM account WHERE account_number BETWEEN '088100000006' AND '088100000014' OR account_number IN ('088200000001','088300000001')"))
                .isEqualTo(11);
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM account WHERE status = 'SUSPENDED' AND account_number IN ('088100000014','088100000007')"))
                .isEqualTo(2);
        assertThat(count(
                        jdbc,
                        "SELECT COUNT(*) FROM account WHERE status = 'CLOSED' AND account_number = '088100000008'"))
                .isEqualTo(1);
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM account WHERE withdrawal_registered = FALSE AND account_number = '088100000012'"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT password_hash FROM customer WHERE user_id = 'honggildong'", String.class))
                .isEqualTo("qa-changed-password-hash");
        assertThat(jdbc.queryForObject("SELECT user_name FROM customer WHERE user_id = 'honggildong'", String.class))
                .isEqualTo("QA 변경 이름");
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM customer WHERE user_id = 'honggildong' AND login_failure_count = 0 AND account_locked = FALSE"))
                .isEqualTo(1);
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM account WHERE account_number = '088100000010' AND balance = 100000 AND password_failure_count = 0 AND password_locked = FALSE AND alias = '주거래 통장'"))
                .isEqualTo(1);
        // 시드 고객은 가입 흐름을 안 타 한도 행이 없으면 이체 경로가 LMT9001 로 거부된다.
        assertThat(
                        count(
                                jdbc,
                                "SELECT COUNT(*) FROM transfer_limit t JOIN customer c ON c.customer_id = t.customer_id WHERE c.user_id IN ('honggildong','kimminji','leeseojun') AND t.one_time_limit = 1000000 AND t.daily_limit = 5000000"))
                .isEqualTo(3);
        // QA 데모 계좌(088100000010~14)가 실가입 채번과 겹치지 않도록 예약됐는지 확인한다.
        assertThat(jdbc.queryForObject(
                        """
                        SELECT last_sequence FROM account_number_sequence
                        WHERE bank_code = '088' AND account_type = 'DEMAND_DEPOSIT' AND product_id IS NULL
                        """,
                        Long.class))
                .isGreaterThanOrEqualTo(14L);
        // QA 데모 계좌 초기 잔액에 대응하는 장부 기록이 잔액과 일치하는지, 재실행돼도
        // 중복 없이 9건(잔액 0원인 K3·K4 제외)만 유지되는지 확인한다 (#378 대사 배치 오탐 방지).
        assertThat(count(jdbc, "SELECT COUNT(*) FROM ledger_entry WHERE transaction_type = 'QA_SEED_INITIAL'"))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject(
                        """
                        SELECT SUM(CASE WHEN direction = 'DEPOSIT' THEN amount ELSE -amount END)
                        FROM ledger_entry WHERE account_id = (
                            SELECT account_id FROM account WHERE account_number = '088100000010'
                        )
                        """,
                        Long.class))
                .isEqualTo(100000L);
    }

    @Test
    @DisplayName(
            "QA 데모 계좌에 QA_SEED_INITIAL 외의 실거래 장부가 쌓인 뒤 재배포돼도 balance를 초기값으로 되돌리지 않는다 (#378 대사 오탐 방지, CodeRabbit 리뷰)")
    void doesNotResetBalance_whenAccountHasRealLedgerActivitySinceSeed() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long accountId =
                jdbc.queryForObject("SELECT account_id FROM account WHERE account_number = '088100000011'", Long.class);

        // QA가 이 계좌로 실제 출금 500원을 했다고 가정 - balance 갱신 + 실거래 장부 추가
        jdbc.update("UPDATE account SET balance = 99500 WHERE account_id = ?", accountId);
        jdbc.update("INSERT INTO ledger_entry_id_sequence () VALUES ()");
        Long ledgerEntryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO ledger_entry
                    (ledger_entry_id, occurred_at, account_id, transaction_number, direction, amount,
                     balance_after, transaction_type, channel, reversed)
                VALUES (?, NOW(6), ?, '20260810WB0000000099', 'WITHDRAWAL', 500, 99500, 'IMMEDIATE_TRANSFER', 'WB', FALSE)
                """,
                ledgerEntryId,
                accountId);

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/seed/local-demo-data.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);

        // balance는 여전히 99500 - 초기값(100000)으로 되돌리면 장부 합계(100000-500=99500)와 어긋난다.
        assertThat(jdbc.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId))
                .isEqualTo(99500L);
        // 장부 합계와 balance가 여전히 일치하는지도 함께 확인한다.
        assertThat(jdbc.queryForObject(
                        """
                        SELECT SUM(CASE WHEN direction = 'DEPOSIT' THEN amount ELSE -amount END)
                        FROM ledger_entry WHERE account_id = ?
                        """,
                        Long.class,
                        accountId))
                .isEqualTo(99500L);
    }

    private int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
