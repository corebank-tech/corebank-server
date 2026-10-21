package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.application.service.PiiVerificationService;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 실제 마트 스키마와 값으로 PII 검증이 통과·실패하는지 본다.
class JdbcMartInspectionAdapterTest {

    private static final String VALID_KEY = "a".repeat(64);

    private final PiiVerificationService service =
            new PiiVerificationService(new JdbcMartInspectionAdapter(InfoEtlTestDatabase::mart));

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("설계 스키마와 가명 키만 있는 마트는 통과한다")
    void cleanMartPasses() throws SQLException {
        execute("INSERT INTO mart_load_batch (base_date, status, started_at) VALUES ('2026-10-20', 'RUNNING', NOW(6))");
        execute("INSERT INTO mart_daily_account_balance (base_date, account_key, customer_key, account_type,"
                + " product_id, product_group, balance, load_batch_id)"
                + " VALUES ('2026-10-20', '" + VALID_KEY + "', '" + VALID_KEY
                + "', 'DEMAND_DEPOSIT', NULL, NULL, 100, 1)");

        PiiVerificationResult result = service.verify();

        assertThat(result.violations()).isEmpty();
        assertThat(result.scannedColumns()).isGreaterThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("키 자리에 원천 계좌번호 같은 12자리 숫자가 들어 있으면 두 가지 위반으로 잡는다")
    void rawAccountNumberInKeyColumnIsCaught() throws SQLException {
        execute("INSERT INTO mart_load_batch (base_date, status, started_at) VALUES ('2026-10-20', 'RUNNING', NOW(6))");
        execute("INSERT INTO mart_daily_account_balance (base_date, account_key, customer_key, account_type,"
                + " product_id, product_group, balance, load_batch_id)"
                + " VALUES ('2026-10-20', '088100000008', '" + VALID_KEY + "', 'DEMAND_DEPOSIT', NULL, NULL, 100, 1)");

        PiiVerificationResult result = service.verify();

        assertThat(result.passed()).isFalse();
        assertThat(result.violations())
                .anyMatch(v -> v.contains("계좌번호 형식") && v.contains("account_key"))
                .anyMatch(v -> v.contains("64자리 16진수") && v.contains("account_key"));
    }

    @Test
    @DisplayName("대문자 16진수 키는 형식 위반이다")
    void uppercaseKeyIsViolation() throws SQLException {
        execute("INSERT INTO mart_load_batch (base_date, status, started_at) VALUES ('2026-10-20', 'RUNNING', NOW(6))");
        execute("INSERT INTO mart_daily_account_balance (base_date, account_key, customer_key, account_type,"
                + " product_id, product_group, balance, load_batch_id)"
                + " VALUES ('2026-10-20', '" + "A".repeat(64) + "', '" + VALID_KEY
                + "', 'DEMAND_DEPOSIT', NULL, NULL, 100, 1)");

        assertThat(service.verify().violations()).anyMatch(v -> v.contains("64자리 16진수"));
    }

    @Test
    @DisplayName("마트에 설계에 없는 컬럼이 추가되면 위반이다")
    void addedColumnIsViolation() throws SQLException {
        execute("ALTER TABLE mart_daily_account_balance ADD COLUMN customer_name VARCHAR(20) NULL");

        assertThat(service.verify().violations()).anyMatch(v -> v.contains("customer_name"));
    }

    private static void execute(String sql) throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart();
                Statement statement = mart.createStatement()) {
            statement.execute(sql);
        }
    }
}
