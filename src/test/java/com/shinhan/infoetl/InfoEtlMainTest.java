package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InfoEtlMainTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    @BeforeEach
    void seedReplicaAndResetMart() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
        InfoEtlTestDatabase.resetMart();
        ReplicaFixtures.insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertLedger(1L, 1L, 300L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
    }

    @Test
    @DisplayName("기준일 인자를 읽는다")
    void parsesBaseDate() {
        assertThat(InfoEtlMain.parseBaseDate(new String[] {"--base-date=2026-10-20"}))
                .isEqualTo(D);
    }

    @Test
    @DisplayName("기준일 인자가 없거나 형식이 틀리면 실패한다")
    void rejectsMissingOrMalformedBaseDate() {
        assertThatThrownBy(() -> InfoEtlMain.parseBaseDate(new String[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InfoEtlMain.parseBaseDate(new String[] {"--base-date=20261020"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("인자나 환경변수가 잘못되면 종료 코드 2를 돌려준다")
    void returnsUsageExitCode() {
        assertThat(InfoEtlMain.run(new String[0], env())).isEqualTo(InfoEtlMain.EXIT_USAGE);
        assertThat(InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, new HashMap<>()))
                .isEqualTo(InfoEtlMain.EXIT_USAGE);
    }

    @Test
    @DisplayName("정상 적재하면 종료 코드 0을 돌려주고 마트에 행이 생긴다")
    void returnsOkAndLoadsMart() throws SQLException {
        int exitCode = InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, env());

        assertThat(exitCode).isEqualTo(InfoEtlMain.EXIT_OK);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(new MartBalanceWriter().countRows(mart, D)).isEqualTo(1L);
            assertThat(latestStatus(mart)).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("적재가 실패하면 종료 코드 1을 돌려주고 FAILED로 기록한다")
    void returnsLoadFailedExitCode() throws SQLException {
        try (Connection replica = InfoEtlTestDatabase.replica();
                Statement statement = replica.createStatement()) {
            statement.execute("DROP TABLE ledger_entry");
        }

        int exitCode = InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, env());

        assertThat(exitCode).isEqualTo(InfoEtlMain.EXIT_LOAD_FAILED);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(latestStatus(mart)).isEqualTo("FAILED");
            assertThat(new MartBalanceWriter().countRows(mart, D)).isZero();
        }
    }

    @Test
    @DisplayName("HMAC 키가 32바이트보다 짧으면 종료 코드 2를 돌려준다")
    void returnsUsageExitCodeForShortKey() {
        Map<String, String> env = env();
        env.put("HMAC_KEY", "too-short");

        assertThat(InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, env))
                .isEqualTo(InfoEtlMain.EXIT_USAGE);
    }

    @Test
    @DisplayName("마트 URL에 묶음 삽입 옵션이 없으면 붙이고 있으면 그대로 둔다")
    void addsBatchRewriteOptionOnlyWhenMissing() {
        assertThat(InfoEtlMain.withBatchRewrite("jdbc:mysql://mart:3307/mart"))
                .isEqualTo("jdbc:mysql://mart:3307/mart?rewriteBatchedStatements=true");
        assertThat(InfoEtlMain.withBatchRewrite("jdbc:mysql://mart:3307/mart?useSSL=false"))
                .isEqualTo("jdbc:mysql://mart:3307/mart?useSSL=false&rewriteBatchedStatements=true");
        assertThat(InfoEtlMain.withBatchRewrite("jdbc:mysql://mart:3307/mart?rewriteBatchedStatements=false"))
                .isEqualTo("jdbc:mysql://mart:3307/mart?rewriteBatchedStatements=false");
    }

    private static Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("REPLICA_JDBC_URL", InfoEtlTestDatabase.replicaUrl());
        env.put("REPLICA_USER", InfoEtlTestDatabase.user());
        env.put("REPLICA_PASSWORD", InfoEtlTestDatabase.password());
        env.put("MART_JDBC_URL", InfoEtlTestDatabase.martUrl());
        env.put("MART_USER", InfoEtlTestDatabase.user());
        env.put("MART_PASSWORD", InfoEtlTestDatabase.password());
        env.put("HMAC_KEY", "main-test-key-0001-0123456789abcdef");
        return env;
    }

    private static String latestStatus(Connection mart) throws SQLException {
        try (Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT status FROM mart_load_batch ORDER BY load_batch_id DESC LIMIT 1")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
