package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.adapter.out.jdbc.JdbcLoadLockAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartLoadAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaBalanceAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaCalendarAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaEventAdapter;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// etl-grants.sql을 테스트 DB에 적용하고, 그 계정들로 할 수 있는 일과 못 하는 일을 실제 접속으로 확인한다.
class EtlGrantsTest {

    private static final String PASSWORD = "CHANGE_ME";
    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    @BeforeEach
    void applyGrants() throws Exception {
        InfoEtlTestDatabase.resetReplica();
        InfoEtlTestDatabase.resetMart();
        try (Connection replica = InfoEtlTestDatabase.replica();
                Statement statement = replica.createStatement()) {
            for (String table :
                    new String[] {"product_subscription", "gl_journal_entry", "batch_execution_history", "customer"}) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + table + " (id BIGINT NOT NULL PRIMARY KEY)");
            }
        }
        try (Connection root = InfoEtlTestDatabase.replica();
                Statement statement = root.createStatement()) {
            for (String sql : readGrantStatements()) {
                statement.execute(sql);
            }
        }
    }

    @Test
    @DisplayName("레플리카 계정은 적재에 필요한 테이블을 읽을 수 있다")
    void replicaReaderCanSelectLoadTables() throws SQLException {
        try (Connection connection = open(InfoEtlTestDatabase.replicaUrl(), "etl_replica_reader");
                Statement statement = connection.createStatement()) {
            for (String table : new String[] {
                "ledger_entry",
                "account",
                "product",
                "product_subscription",
                "gl_journal_entry",
                "holiday",
                "business_date_cutoff"
            }) {
                statement.executeQuery("SELECT COUNT(*) FROM " + table).close();
            }
        }
    }

    @Test
    @DisplayName("레플리카 계정은 쓸 수 없고 개인정보 테이블도 읽을 수 없다")
    void replicaReaderCannotWriteOrReadCustomer() throws SQLException {
        try (Connection connection = open(InfoEtlTestDatabase.replicaUrl(), "etl_replica_reader");
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("DELETE FROM holiday"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> statement.execute("DELETE FROM ledger_entry"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> statement.execute("DELETE FROM business_date_cutoff"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> statement.execute("INSERT INTO holiday VALUES ('2026-12-25', '성탄절')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM customer"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            // 시작 신호는 business_date_cutoff뿐이라 관측용 실행 기록은 읽을 이유가 없다(설계 §3-4).
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM batch_execution_history"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("마트 쓰기 계정은 마트를 읽고 쓰지만 테이블을 지우거나 레플리카를 읽을 수 없다")
    void martWriterCanWriteMartButNotAlterOrReadReplica() throws SQLException {
        try (Connection connection = open(InfoEtlTestDatabase.martUrl(), "etl_mart_writer");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO mart_load_batch (base_date, status, started_at) VALUES ('2026-10-20', 'RUNNING', NOW(6))");
            statement.execute("UPDATE mart_load_batch SET status = 'FAILED'");
            statement.execute("DELETE FROM mart_load_batch");
            assertThatThrownBy(() -> statement.execute("DROP TABLE mart_load_batch"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM replica_db.ledger_entry"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("정보계 API 계정은 마트를 읽기만 한다")
    void apiReaderIsReadOnly() throws SQLException {
        try (Connection connection = open(InfoEtlTestDatabase.martUrl(), "info_api_reader");
                Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT COUNT(*) FROM mart_load_batch").close();
            assertThatThrownBy(() -> statement.execute("DELETE FROM mart_load_batch"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
        }
    }

    @Test
    @DisplayName("이 권한만으로 적재가 끝까지 돈다")
    void loadRunsWithTheLimitedAccounts() throws SQLException {
        ReplicaFixtures.insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertLedger(1L, 1L, 300L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
        String replicaUrl = InfoEtlTestDatabase.replicaUrl();
        String martUrl = InfoEtlTestDatabase.martUrl();
        DailyLoadService service = new DailyLoadService(
                new JdbcReplicaBalanceAdapter(() -> open(replicaUrl, "etl_replica_reader")),
                new JdbcReplicaEventAdapter(() -> open(replicaUrl, "etl_replica_reader")),
                new JdbcReplicaCalendarAdapter(() -> open(replicaUrl, "etl_replica_reader")),
                new JdbcMartLoadAdapter(() -> open(martUrl, "etl_mart_writer"), Clock.system(ZoneId.of("Asia/Seoul"))),
                new JdbcLoadLockAdapter(() -> open(martUrl, "etl_mart_writer")),
                new Pseudonymizer(TestKeys.bytes("g")));

        LoadResult result = service.load(D);

        assertThat(result.balanceRowCount()).isEqualTo(1L);
        assertThat(result.martBalanceTotal()).isEqualTo(300L);
    }

    private static Connection open(String url, String user) throws SQLException {
        return DriverManager.getConnection(url, user, PASSWORD);
    }

    // 테스트 DB의 이름(replica_db·mart_db)에 맞춰 스키마 이름만 바꿔 적용한다.
    private static java.util.List<String> readGrantStatements() throws IOException {
        try (InputStream in = EtlGrantsTest.class.getResourceAsStream("/etl-grants.sql")) {
            assertThat(in).as("etl-grants.sql 리소스").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"))
                    .replace("minicore.", "replica_db.")
                    .replace("info_mart.", "mart_db.");
            return Arrays.stream(sql.split(";"))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
