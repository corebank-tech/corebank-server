package com.shinhan.corebank.business.application.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.application.port.in.BusinessDateCatchUpUseCase;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 커밋 순서가 검증 대상이라 @Transactional 을 걸지 않는다. 영업일과 cutoff 는 테스트마다 비우고 되돌린다
class BusinessDateCutoffIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);

    @Autowired
    BusinessDateGate gate;

    @Autowired
    BusinessDateAdvancer advancer;

    @Autowired
    BusinessDateCatchUpUseCase catchUp;

    @Autowired
    BusinessDateProvider provider;

    @Autowired
    BusinessDateTestFixture fixture;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private ExecutorService executor;
    private LocalDate original;
    private LocalDate next;

    @BeforeEach
    void setUp() {
        original = fixture.currentInDb();
        fixture.moveTo(BASE);
        next = provider.nextBusinessDay(BASE);
        jdbc.update("DELETE FROM business_date_cutoff");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS gate_probe (id BIGINT AUTO_INCREMENT PRIMARY KEY, trade_date DATE NOT NULL)");
        jdbc.update("DELETE FROM gate_probe");
        tx = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        fixture.moveTo(original);
        jdbc.update("DELETE FROM business_date_cutoff");
        jdbc.execute("DROP TABLE IF EXISTS gate_probe");
    }

    @Test
    @DisplayName("전환하면 떠나는 영업일의 cutoff 가 CLOSED 로 정확히 한 행 생긴다")
    void advanceRecordsExactlyOneClosedCutoff() {
        advancer.advanceFrom(BASE);

        assertThat(cutoffDates()).containsExactly(BASE);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM business_date_cutoff WHERE base_date = ?", String.class, BASE))
                .isEqualTo("CLOSED");
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    @Test
    @DisplayName("이미 넘어간 영업일을 다시 전환해도 행이 늘지 않고 첫 closed_at 이 유지된다")
    void rerunDoesNotDuplicateOrOverwrite() {
        advancer.advanceFrom(BASE);
        LocalDateTime firstClosedAt = closedAt(BASE);

        advancer.advanceFrom(BASE);
        advancer.advanceFrom(BASE);

        assertThat(cutoffDates()).containsExactly(BASE);
        assertThat(closedAt(BASE)).isEqualTo(firstClosedAt);
    }

    @Test
    @DisplayName("cutoff 없이 넘어간 예전 영업일을 재실행해도 행을 만들지 않는다")
    void rerunDoesNotBackfillMissingCutoff() {
        fixture.moveTo(next);
        assertThat(cutoffDates()).isEmpty();

        advancer.advanceFrom(BASE);

        assertThat(cutoffDates()).isEmpty();
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    @Test
    @DisplayName("전환과 cutoff 는 한 트랜잭션이라 같이 롤백된다")
    void advanceAndCutoffRollBackTogether() {
        tx.executeWithoutResult(status -> {
            advancer.advanceFrom(BASE);
            status.setRollbackOnly();
        });

        assertThat(fixture.currentInDb()).isEqualTo(BASE);
        assertThat(cutoffDates()).isEmpty();
    }

    @Test
    @DisplayName("달력 따라잡기가 영업일을 건너뛰면 떠나는 날과 건너뛴 영업일 모두 cutoff 를 남기고 주말은 남기지 않는다")
    void catchUpRecordsEveryBusinessDayLeft() {
        // 2026-10-14(수) -> 10-20(화): 14, 15, 16, 19 를 마감하고 17·18(주말)은 남기지 않는다
        catchUp.catchUpTo(LocalDate.of(2026, 10, 20));

        assertThat(fixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(cutoffDates())
                .containsExactly(
                        LocalDate.of(2026, 10, 14),
                        LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 10, 16),
                        LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("이미 달력과 같으면 따라잡기는 아무것도 남기지 않는다")
    void catchUpWhenAlreadyCurrentRecordsNothing() {
        catchUp.catchUpTo(BASE);

        assertThat(fixture.currentInDb()).isEqualTo(BASE);
        assertThat(cutoffDates()).isEmpty();
    }

    @Test
    @DisplayName("cutoff 를 처음 본 시점의 스냅샷에는 그 영업일을 읽은 거래의 쓰기가 모두 보인다")
    void whenCutoffIsVisibleEveryWriteOfThatDayIsVisible() throws Exception {
        List<Future<LocalDate>> writers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            long startDelay = i * 40L;
            writers.add(executor.submit(() -> {
                Thread.sleep(startDelay);
                return tx.execute(status -> {
                    LocalDate tradeDate = gate.acquireSharedAndReadBusinessDate();
                    jdbc.update("INSERT INTO gate_probe (trade_date) VALUES (?)", Date.valueOf(tradeDate));
                    sleepQuietly(150);
                    return tradeDate;
                });
            }));
        }

        AtomicReference<Long> visibleAtCutoff = new AtomicReference<>();
        CountDownLatch observerStarted = new CountDownLatch(1);
        Future<?> observer = executor.submit(() -> {
            observerStarted.countDown();
            while (visibleAtCutoff.get() == null) {
                tx.executeWithoutResult(status -> {
                    Integer closed = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM business_date_cutoff WHERE base_date = ?", Integer.class, BASE);
                    if (closed != null && closed == 1) {
                        // 같은 트랜잭션의 두 번째 읽기는 cutoff 를 본 스냅샷과 같다
                        visibleAtCutoff.set(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM gate_probe WHERE trade_date = ?",
                                Long.class,
                                Date.valueOf(BASE)));
                    }
                });
                sleepQuietly(5);
            }
        });
        observerStarted.await(5, SECONDS);

        Thread.sleep(250);
        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));

        List<LocalDate> readDates = new ArrayList<>();
        for (Future<LocalDate> writer : writers) {
            readDates.add(writer.get(30, SECONDS));
        }
        assertThat(advance.get(30, SECONDS)).isEqualTo(next);
        observer.get(30, SECONDS);

        long baseWrites = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gate_probe WHERE trade_date = ?", Long.class, Date.valueOf(BASE));
        // 두 날짜가 모두 나와야 경합이 실제로 일어난 테스트다
        assertThat(readDates).contains(BASE, next);
        assertThat(readDates).allMatch(date -> date.equals(BASE) || date.equals(next));
        assertThat(visibleAtCutoff.get()).isEqualTo(baseWrites);
        assertThat(baseWrites).isEqualTo(readDates.stream().filter(BASE::equals).count());
    }

    private List<LocalDate> cutoffDates() {
        return jdbc.queryForList("SELECT base_date FROM business_date_cutoff ORDER BY base_date", LocalDate.class);
    }

    private LocalDateTime closedAt(LocalDate baseDate) {
        return jdbc.queryForObject(
                "SELECT closed_at FROM business_date_cutoff WHERE base_date = ?", LocalDateTime.class, baseDate);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
