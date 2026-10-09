package com.shinhan.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V202610070600(PH-24) 이 기존 전표 행의 참조 키를 채우는지 확인한다.
 *
 * <p>PH-60b 시드가 이 V 파일보다 먼저 운영에 들어가므로, 그 직전 버전까지 올린 빈 스키마에 시드 모양의 행을 넣고 나머지를
 * 적용한다. 스프링 컨텍스트 없이 스키마를 따로 만든다 — 다른 테스트의 스키마에는 이미 최신 버전이 올라가 있다.
 */
class GlVoucherReferenceKeyMigrationTest {

    private static final String VERSION_BEFORE = "202610051913";

    @Test
    @DisplayName("이체·상품가입 전표는 description 을, 개시 전표는 OPENING-거래일을 참조 키로 채운다")
    void backfillsReferenceKeyFromSeedRows() {
        DriverManagerDataSource dataSource = newSchema();
        migrate(dataSource, VERSION_BEFORE);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertVoucher(jdbc, "20260901-OPN-000001", "OPENING", "개시 잔액");
        insertVoucher(jdbc, "20260901-TRF-000001", "TRANSFER", "20260901WB0060000001");
        insertVoucher(jdbc, "20260901-SUB-000001", "PRODUCT_SUBSCRIPTION", "20260901WB0060000002");

        migrate(dataSource, null);

        Map<String, String> keys = new java.util.HashMap<>();
        jdbc.query("SELECT voucher_no, reference_key FROM gl_voucher", row -> {
            keys.put(row.getString("voucher_no"), row.getString("reference_key"));
        });
        assertThat(keys)
                .containsEntry("20260901-OPN-000001", "OPENING-20260901")
                .containsEntry("20260901-TRF-000001", "20260901WB0060000001")
                .containsEntry("20260901-SUB-000001", "20260901WB0060000002");
    }

    @Test
    @DisplayName("키를 채울 수 없는 행이 남으면 마이그레이션이 실패한다")
    void failsWhenReferenceKeyCannotBeFilled() {
        DriverManagerDataSource dataSource = newSchema();
        migrate(dataSource, VERSION_BEFORE);
        insertVoucher(new JdbcTemplate(dataSource), "20260901-TRF-000001", "TRANSFER", null);

        assertThatThrownBy(() -> migrate(dataSource, null)).isInstanceOf(FlywayException.class);
    }

    private static DriverManagerDataSource newSchema() {
        SharedTestContainers.Namespace namespace =
                SharedTestContainers.instance().newNamespace();
        return new DriverManagerDataSource(namespace.jdbcUrl(), namespace.username(), namespace.password());
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void insertVoucher(JdbcTemplate jdbc, String voucherNo, String txType, String description) {
        jdbc.update(
                "INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, description) VALUES (?, '2026-09-01', ?, ?)",
                voucherNo,
                txType,
                description);
    }
}
