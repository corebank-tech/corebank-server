package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MartSchemaTest {

    @BeforeEach
    void applySchema() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("mart_daily_account_balance 컬럼이 설계와 같다")
    void dailyAccountBalanceColumns() throws SQLException {
        assertThat(columns("mart_daily_account_balance"))
                .containsExactly(
                        "base_date date NO",
                        "account_key char(64) NO",
                        "customer_key char(64) NO",
                        "account_type varchar(24) NO",
                        "product_id bigint YES",
                        "product_group varchar(12) YES",
                        "balance bigint NO",
                        "load_batch_id bigint NO");
        assertThat(indexNames("mart_daily_account_balance")).containsExactlyInAnyOrder("PRIMARY", "ix_mdab_product");
    }

    @Test
    @DisplayName("mart_product_subscription_event 컬럼이 설계와 같다")
    void productSubscriptionEventColumns() throws SQLException {
        assertThat(columns("mart_product_subscription_event"))
                .containsExactly(
                        "base_date date NO",
                        "event_type varchar(12) NO",
                        "account_key char(64) NO",
                        "customer_key char(64) NO",
                        "product_id bigint NO",
                        "product_code varchar(20) NO",
                        "product_name varchar(100) NO",
                        "product_group varchar(12) NO",
                        "load_batch_id bigint NO");
        assertThat(indexNames("mart_product_subscription_event"))
                .containsExactlyInAnyOrder("PRIMARY", "uk_mpse_account_event", "ix_mpse_product");
    }

    @Test
    @DisplayName("mart_load_batch 컬럼이 설계와 같다")
    void loadBatchColumns() throws SQLException {
        assertThat(columns("mart_load_batch"))
                .containsExactly(
                        "load_batch_id bigint NO",
                        "base_date date NO",
                        "status varchar(12) NO",
                        "started_at datetime(6) NO",
                        "completed_at datetime(6) YES",
                        "balance_row_count bigint YES",
                        "event_row_count bigint YES",
                        "mart_balance_total bigint YES",
                        "ledger_total bigint YES",
                        "gl_compare_from_date date YES",
                        "mart_balance_movement bigint YES",
                        "gl_deposit_movement bigint YES",
                        "as_of_date date YES",
                        "failure_reason varchar(200) YES");
        assertThat(indexNames("mart_load_batch")).containsExactlyInAnyOrder("PRIMARY", "ix_mlb_date_status");
    }

    @Test
    @DisplayName("가명 키 컬럼 주석이 종류 접두어를 입력으로 밝힌다")
    void pseudonymKeyCommentsNamePrefixedInput() throws SQLException {
        assertThat(columnComment("mart_daily_account_balance", "account_key"))
                .isEqualTo("HMAC-SHA256(account:{account_id}) hex");
        assertThat(columnComment("mart_daily_account_balance", "customer_key"))
                .isEqualTo("HMAC-SHA256(customer:{customer_id}) hex");
    }

    @Test
    @DisplayName("스키마에는 마트 테이블 세 개만 생긴다")
    void createsOnlyThreeTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = InfoEtlTestDatabase.mart();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }

        assertThat(tables)
                .containsExactlyInAnyOrder(
                        "mart_daily_account_balance", "mart_product_subscription_event", "mart_load_batch");
    }

    @Test
    @DisplayName("PII 검증의 컬럼 화이트리스트는 적용한 마트 DDL의 컬럼과 정확히 같다")
    void whitelistMatchesAppliedSchema() throws SQLException {
        for (var entry : com.shinhan.infoetl.domain.MartSchemaWhitelist.COLUMNS.entrySet()) {
            List<String> actualNames = columns(entry.getKey()).stream()
                    .map(column -> column.substring(0, column.indexOf(' ')))
                    .toList();
            assertThat(actualNames).isEqualTo(entry.getValue());
        }
    }

    private static List<String> columns(String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = InfoEtlTestDatabase.mart();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT column_name, column_type, is_nullable FROM information_schema.columns"
                                + " WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString(1) + " " + resultSet.getString(2) + " " + resultSet.getString(3));
                }
            }
        }
        return result;
    }

    private static String columnComment(String table, String column) throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.mart();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT column_comment FROM information_schema.columns"
                                + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static List<String> indexNames(String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = InfoEtlTestDatabase.mart();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT DISTINCT index_name FROM information_schema.statistics"
                                + " WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString(1));
                }
            }
        }
        return result;
    }
}
