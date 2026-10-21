package com.shinhan.infoetl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// JVM당 MySQL 한 대에 레플리카용·마트용 DB 두 개를 만든다. 종료는 Ryuk에 맡긴다.
public final class InfoEtlTestDatabase {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.10"));

    private static final List<String> REPLICA_DDL = List.of(
            "DROP TABLE IF EXISTS ledger_entry",
            "DROP TABLE IF EXISTS account",
            "DROP TABLE IF EXISTS product",
            "DROP TABLE IF EXISTS product_subscription",
            "DROP TABLE IF EXISTS holiday",
            "DROP TABLE IF EXISTS business_date_cutoff",
            "CREATE TABLE ledger_entry (ledger_entry_id BIGINT NOT NULL, account_id BIGINT NOT NULL,"
                    + " transaction_number CHAR(20) NULL, direction VARCHAR(10) NULL,"
                    + " balance_after BIGINT NOT NULL, transaction_type VARCHAR(32) NULL,"
                    + " occurred_at DATETIME(6) NOT NULL, trade_date DATE NULL,"
                    + " PRIMARY KEY (ledger_entry_id, occurred_at))",
            "CREATE TABLE account (account_id BIGINT NOT NULL PRIMARY KEY, customer_id BIGINT NOT NULL,"
                    + " product_id BIGINT NULL, account_type VARCHAR(24) NOT NULL, status VARCHAR(12) NOT NULL,"
                    + " closed_trade_date DATE NULL)",
            "CREATE TABLE product (product_id BIGINT NOT NULL PRIMARY KEY, product_code VARCHAR(20) NOT NULL,"
                    + " product_name VARCHAR(100) NOT NULL, product_group VARCHAR(12) NOT NULL)",
            "CREATE TABLE product_subscription (subscription_id BIGINT NOT NULL PRIMARY KEY,"
                    + " customer_id BIGINT NOT NULL, product_id BIGINT NOT NULL, account_id BIGINT NULL,"
                    + " status VARCHAR(12) NOT NULL, subscribed_at DATETIME(6) NULL, subscription_trade_date DATE NULL)",
            "CREATE TABLE holiday (holiday_date DATE NOT NULL PRIMARY KEY, holiday_name VARCHAR(50) NOT NULL)",
            "CREATE TABLE business_date_cutoff (business_date_cutoff_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + " base_date DATE NOT NULL, status VARCHAR(10) NOT NULL, closed_at DATETIME(6) NOT NULL,"
                    + " UNIQUE KEY uk_cutoff (base_date, status))");

    private static final List<String> MART_TABLES =
            List.of("mart_daily_account_balance", "mart_product_subscription_event", "mart_load_batch");

    static {
        MYSQL.start();
        try (Connection connection = connect("");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE replica_db");
            statement.execute("CREATE DATABASE mart_db");
        } catch (SQLException e) {
            throw new IllegalStateException("테스트 DB 생성에 실패했습니다.", e);
        }
    }

    private InfoEtlTestDatabase() {}

    public static Connection replica() throws SQLException {
        return connect("replica_db");
    }

    public static Connection mart() throws SQLException {
        return connect("mart_db");
    }

    public static void resetReplica() throws SQLException {
        try (Connection connection = replica();
                Statement statement = connection.createStatement()) {
            for (String sql : REPLICA_DDL) {
                statement.execute(sql);
            }
        }
    }

    public static void resetMart() throws SQLException {
        try (Connection connection = mart();
                Statement statement = connection.createStatement()) {
            for (String table : MART_TABLES) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
            for (String sql : readSchemaStatements()) {
                statement.execute(sql);
            }
        }
    }

    public static String replicaUrl() {
        return url("replica_db");
    }

    public static String martUrl() {
        return url("mart_db");
    }

    public static String user() {
        return "root";
    }

    public static String password() {
        return MYSQL.getPassword();
    }

    private static String url(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database;
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), user(), password());
    }

    // 주석 줄을 빼고 세미콜론으로 문장을 나눈다. 스키마 파일에는 문자열 안 세미콜론이 없다.
    private static List<String> readSchemaStatements() {
        try (InputStream in = InfoEtlTestDatabase.class.getResourceAsStream("/mart-schema.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(sql.split(";"))
                    .map(String::strip)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("mart-schema.sql을 읽지 못했습니다.", e);
        }
    }
}
