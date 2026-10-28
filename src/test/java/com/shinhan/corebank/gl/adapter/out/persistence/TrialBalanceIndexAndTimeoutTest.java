package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("시산표 집계 인덱스와 쿼리 타임아웃(GL-IDX) 통합 테스트")
class TrialBalanceIndexAndTimeoutTest extends IntegrationTestSupport {

    private static final String INDEX_COLUMNS_SQL = "SELECT column_name FROM information_schema.statistics"
            + " WHERE table_schema = DATABASE() AND table_name = 'gl_journal_entry' AND index_name = ?"
            + " ORDER BY seq_in_index";

    // 시드와 다른 GL 테스트가 쓰지 않는 거래일
    private static final LocalDate DAY = LocalDate.of(2031, 2, 1);

    @Autowired
    private TrialBalanceQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("집계 인덱스는 거래일이 먼저이고 집계에 쓰는 컬럼을 모두 담는다")
    void trialBalanceIndexStartsWithTradeDateAndCoversAggregate() {
        assertThat(jdbcTemplate.queryForList(INDEX_COLUMNS_SQL, String.class, "ix_gl_journal_entry_trade_date"))
                .containsExactly("trade_date", "account_code", "dr_cr", "amount");
    }

    // account_code 로 시작하는 인덱스를 더하면 MySQL 이 FK 가 자동으로 만든 이 인덱스를 조용히 지운다.
    // 그러면 schema_reference.md 와 실제 스키마가 어긋나고 새 인덱스를 되돌릴 수 없게 된다(오류 1553).
    @Test
    @DisplayName("계정 FK 인덱스가 그대로 남아 있다")
    void accountForeignKeyIndexSurvives() {
        assertThat(jdbcTemplate.queryForList(INDEX_COLUMNS_SQL, String.class, "fk_gl_journal_entry_account"))
                .containsExactly("account_code");
    }

    // 서비스의 @Transactional(timeout) 이 이 네이티브 집계 쿼리에 JDBC 쿼리 타임아웃으로 실제로 걸리는지 본다.
    // 다른 커넥션이 분개 테이블 쓰기 잠금을 쥐고 있으면 집계는 잠금을 기다리다 1초 타임아웃에 끊겨야 한다.
    // 타임아웃이 걸리지 않으면 무한히 기다리므로 별도 스레드에서 돌려 10초 안에 안 끝나면 실패로 본다.
    @Test
    @DisplayName("트랜잭션 타임아웃을 넘긴 집계는 잠금을 계속 기다리지 않고 쿼리 타임아웃으로 끊긴다")
    void aggregateIsCutAtTransactionTimeout() throws Exception {
        TransactionTemplate oneSecond = new TransactionTemplate(transactionManager);
        oneSecond.setReadOnly(true);
        oneSecond.setTimeout(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection locker = dataSource.getConnection();
                Statement statement = locker.createStatement()) {
            statement.execute("LOCK TABLES gl_journal_entry WRITE");
            try {
                long start = System.nanoTime();
                Future<?> aggregate =
                        executor.submit(() -> oneSecond.execute(status -> adapter.sumByAccount(DAY, DAY)));

                assertThatThrownBy(() -> aggregate.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(QueryTimeoutException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
            } finally {
                statement.execute("UNLOCK TABLES");
                executor.shutdownNow();
            }
        }

        List<TrialBalanceLine> afterUnlock = oneSecond.execute(status -> adapter.sumByAccount(DAY, DAY));
        assertThat(afterUnlock).isEmpty();
    }
}
