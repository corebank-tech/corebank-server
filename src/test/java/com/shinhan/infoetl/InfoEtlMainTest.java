package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.adapter.in.cli.EtlCli;
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
    @DisplayName("인자나 환경변수가 잘못되면 종료 코드 2를 돌려준다")
    void returnsUsageExitCode() {
        assertThat(InfoEtlMain.run(new String[0], env())).isEqualTo(EtlCli.EXIT_USAGE);
        assertThat(InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, new HashMap<>()))
                .isEqualTo(EtlCli.EXIT_USAGE);
    }

    @Test
    @DisplayName("정상 적재하면 종료 코드 0을 돌려주고 마트에 행이 생긴다")
    void returnsOkAndLoadsMart() throws SQLException {
        int exitCode = InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, env());

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(countBalanceRows(mart)).isEqualTo(1L);
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

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_LOAD_FAILED);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(latestStatus(mart)).isEqualTo("FAILED");
            assertThat(countBalanceRows(mart)).isZero();
        }
    }

    @Test
    @DisplayName("HMAC 키가 32바이트보다 짧으면 종료 코드 2를 돌려준다")
    void returnsUsageExitCodeForShortKey() {
        Map<String, String> env = env();
        env.put("HMAC_KEY", "too-short");

        assertThat(InfoEtlMain.run(new String[] {"--base-date=2026-10-20"}, env))
                .isEqualTo(EtlCli.EXIT_USAGE);
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

    @Test
    @DisplayName("경계일 환경변수 없이 --auto를 주면 사용법 오류로 끝난다")
    void autoWithoutBoundaryIsUsageError() {
        assertThat(InfoEtlMain.run(new String[] {"--auto"}, env())).isEqualTo(EtlCli.EXIT_USAGE);
    }

    @Test
    @DisplayName("경계일이 있고 cutoff가 아직 없으면 --auto는 아무것도 적재하지 않고 정상 종료한다")
    void autoWithoutCutoffLoadsNothing() throws SQLException {
        Map<String, String> env = env();
        env.put("ETL_CUTOFF_BOUNDARY_DATE", "2026-10-20");

        int exitCode = InfoEtlMain.run(new String[] {"--auto"}, env);

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        try (Connection mart = InfoEtlTestDatabase.mart();
                Statement statement = mart.createStatement();
                ResultSet batches = statement.executeQuery("SELECT COUNT(*) FROM mart_load_batch")) {
            batches.next();
            assertThat(batches.getLong(1)).isZero();
        }
    }

    @Test
    @DisplayName("경계일 cutoff가 있으면 --auto는 그 날짜를 적재한다")
    void autoWithCutoffLoadsBoundaryDate() throws SQLException {
        ReplicaFixtures.insertCutoff(D);
        Map<String, String> env = env();
        env.put("ETL_CUTOFF_BOUNDARY_DATE", D.toString());

        int exitCode = InfoEtlMain.run(new String[] {"--auto"}, env);

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(countBalanceRows(mart)).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("섞어 쓴 인자는 종료 코드 2를 돌려준다")
    void mixedArgumentsReturnUsageExitCode() {
        assertThat(InfoEtlMain.run(
                        new String[] {"--base-date=2026-10-20", "--from=2026-10-19", "--to=2026-10-21"}, env()))
                .isEqualTo(EtlCli.EXIT_USAGE);
    }

    @Test
    @DisplayName("기간 적재는 주말과 공휴일을 건너뛰고 영업일만 마트에 넣고 종료 코드 0을 돌려준다")
    void rangeLoadSkipsNonBusinessDays() throws SQLException {
        ReplicaFixtures.insertLedger(5L, 1L, 100L, LocalDateTime.of(2026, 10, 16, 9, 0), LocalDate.of(2026, 10, 16));
        ReplicaFixtures.insertHoliday(LocalDate.of(2026, 10, 21), "임시 공휴일");

        int exitCode = InfoEtlMain.run(new String[] {"--from=2026-10-17", "--to=2026-10-22"}, env());

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(loadedBaseDates(mart))
                    .containsExactly(LocalDate.of(2026, 10, 19), D, LocalDate.of(2026, 10, 22));
        }
    }

    @Test
    @DisplayName("기간 중 하루라도 실패하면 나머지는 적재하고 종료 코드 1을 돌려준다")
    void rangeLoadReturnsFailureCodeButKeepsLoadingOthers() throws SQLException {
        // 10/19는 그날까지의 원장이 없어 잔액이 0건이라 실패하고, 10/20·21은 적재된다.
        int exitCode = InfoEtlMain.run(new String[] {"--from=2026-10-19", "--to=2026-10-21"}, env());

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_LOAD_FAILED);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(loadedBaseDates(mart)).containsExactly(D, LocalDate.of(2026, 10, 21));
        }
    }

    private static Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("REPLICA_JDBC_URL", InfoEtlTestDatabase.replicaUrl());
        env.put("REPLICA_USER", InfoEtlTestDatabase.user());
        env.put("REPLICA_PASSWORD", InfoEtlTestDatabase.password());
        env.put("MART_JDBC_URL", InfoEtlTestDatabase.martUrl());
        env.put("MART_USER", InfoEtlTestDatabase.user());
        env.put("MART_PASSWORD", InfoEtlTestDatabase.password());
        env.put("HMAC_KEY", TestKeys.text("m"));
        return env;
    }

    private static java.util.List<LocalDate> loadedBaseDates(Connection mart) throws SQLException {
        java.util.List<LocalDate> dates = new java.util.ArrayList<>();
        try (Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT DISTINCT base_date FROM mart_daily_account_balance ORDER BY base_date")) {
            while (resultSet.next()) {
                dates.add(resultSet.getObject(1, LocalDate.class));
            }
        }
        return dates;
    }

    private static long countBalanceRows(Connection mart) throws SQLException {
        try (Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM mart_daily_account_balance")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
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
