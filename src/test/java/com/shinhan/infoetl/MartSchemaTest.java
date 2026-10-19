package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class MartSchemaTest {

    private static MySQLContainer mysql;

    @BeforeAll
    static void startMysqlAndApplySchema() throws Exception {
        mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4.10"));
        mysql.start();
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            for (String sql : readStatements()) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    static void stopMysql() {
        mysql.stop();
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
                        "failure_reason varchar(200) YES");
        assertThat(indexNames("mart_load_batch")).containsExactlyInAnyOrder("PRIMARY", "ix_mlb_date_status");
    }

    @Test
    @DisplayName("스키마에는 마트 테이블 세 개만 생긴다")
    void createsOnlyThreeTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = connect();
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

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    // 주석 줄을 빼고 세미콜론으로 문장을 나눈다. 스키마 파일에는 문자열 안 세미콜론이 없다.
    private static List<String> readStatements() throws IOException {
        try (InputStream in = MartSchemaTest.class.getResourceAsStream("/mart-schema.sql")) {
            assertThat(in).as("mart-schema.sql 리소스").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return java.util.Arrays.stream(sql.split(";"))
                    .map(String::strip)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
        }
    }

    private static List<String> columns(String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = connect();
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

    private static List<String> indexNames(String table) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = connect();
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
