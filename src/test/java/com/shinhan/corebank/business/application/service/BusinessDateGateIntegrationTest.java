package com.shinhan.corebank.business.application.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 락 대기·커밋 순서가 검증 대상이라 @Transactional 을 걸지 않고, 영업일은 테스트마다 되돌린다
class BusinessDateGateIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);

    @Autowired
    BusinessDateGate gate;

    @Autowired
    BusinessDateAdvancer advancer;

    @Autowired
    BusinessDateProvider provider;

    @Autowired
    BusinessDateTestFixture fixture;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private TransactionTemplate requiresNewTx;
    private ExecutorService executor;
    private LocalDate original;
    private LocalDate next;

    @BeforeEach
    void setUp() {
        original = fixture.currentInDb();
        fixture.moveTo(BASE);
        next = provider.nextBusinessDay(BASE);
        tx = new TransactionTemplate(transactionManager);
        requiresNewTx = new TransactionTemplate(transactionManager);
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        executor = Executors.newFixedThreadPool(6);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        fixture.moveTo(original);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 락이 읽자마자 풀리므로 예외로 막는다")
    void outsideTransactionIsRejected() {
        assertThatThrownBy(() -> gate.acquireSharedAndReadBusinessDate())
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("같은 트랜잭션 재호출은 다시 읽지 않고 첫 값을 돌려준다")
    void recallReturnsFirstValueWithoutReading() {
        tx.executeWithoutResult(status -> {
            LocalDate first = gate.acquireSharedAndReadBusinessDate();
            jdbc.update("UPDATE business_date SET business_date = ? WHERE date_type = 'BUSINESS_DATE'", next);
            // 다시 읽었다면 next 가 나온다 — 첫 값이 나오면 기억한 것이다
            assertThat(fixture.currentInDb()).isEqualTo(next);
            assertThat(gate.acquireSharedAndReadBusinessDate()).isEqualTo(first).isEqualTo(BASE);
            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("같은 스레드의 다음 트랜잭션에는 이전 트랜잭션의 값이 새지 않는다")
    void rememberedValueDoesNotLeakIntoNextTransaction() {
        LocalDate first = tx.execute(status -> gate.acquireSharedAndReadBusinessDate());
        fixture.moveTo(next);

        LocalDate second = tx.execute(status -> gate.acquireSharedAndReadBusinessDate());

        assertThat(first).isEqualTo(BASE);
        assertThat(second).isEqualTo(next);
    }

    @Test
    @DisplayName("트랜잭션이 이미 일반 읽기로 스냅샷을 잡았어도 gate 는 커밋된 최신 값을 읽는다")
    void gateReadsLatestCommittedEvenWhenSnapshotIsOlder() throws Exception {
        tx.executeWithoutResult(status -> {
            assertThat(provider.today()).isEqualTo(BASE);
            // 다른 연결에서 전환이 커밋된다 — 이 트랜잭션은 아직 락을 잡지 않았다
            await(executor.submit(() -> fixture.moveTo(next)));

            assertThat(gate.acquireSharedAndReadBusinessDate()).isEqualTo(next);
            // 일반 읽기는 스냅샷이라 옛 값이다 — 해지 거래일을 today() 로 저장하면 안 되는 이유
            assertThat(provider.today()).isEqualTo(BASE);
        });
    }

    @Test
    @DisplayName("영업일 행이 없으면 BUSINESS_DATE_NOT_FOUND")
    void missingRowThrowsNotFound() {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM business_date WHERE date_type = 'BUSINESS_DATE'");
            assertThatThrownBy(() -> gate.acquireSharedAndReadBusinessDate())
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(CommonErrorCode.BUSINESS_DATE_NOT_FOUND));
            status.setRollbackOnly();
        });
        assertThat(fixture.currentInDb()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션에서도 공유 락을 잡을 수 있다")
    void worksInReadOnlyTransaction() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        LocalDate businessDate = readOnly.execute(status -> gate.acquireSharedAndReadBusinessDate());

        assertThat(businessDate).isEqualTo(BASE);
    }

    @Test
    @DisplayName("공유 락끼리는 서로를 막지 않는다 — 거래가 직렬화되면 안 된다")
    void sharedHoldersDoNotBlockEachOther() throws Exception {
        GateHolder holder = new GateHolder(false);

        Future<LocalDate> other = executor.submit(() -> tx.execute(status -> gate.acquireSharedAndReadBusinessDate()));

        assertThat(other.get(2, SECONDS)).isEqualTo(BASE);
        holder.release();
    }

    @Test
    @DisplayName("공유 락을 쥔 거래가 끝나기 전에는 영업일 전환이 대기한다")
    void advanceWaitsForSharedHolder() throws Exception {
        GateHolder holder = new GateHolder(false);

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        assertBlocked(advance);
        assertThat(fixture.currentInDb()).isEqualTo(BASE);

        holder.release();
        assertThat(advance.get(10, SECONDS)).isEqualTo(next);
        assertThat(holder.result()).isEqualTo(BASE);
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    @Test
    @DisplayName("거래가 롤백해도 락이 풀려 영업일 전환이 진행된다")
    void rollbackReleasesLock() throws Exception {
        GateHolder holder = new GateHolder(true);

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        assertBlocked(advance);

        holder.release();
        assertThat(advance.get(10, SECONDS)).isEqualTo(next);
    }

    @Test
    @DisplayName("전환이 대기 중일 때 뒤에 온 새 거래는 그 뒤에 줄을 서고 전환 뒤 날짜를 읽는다")
    void newTransactionQueuesBehindWaitingAdvance() throws Exception {
        GateHolder holder = new GateHolder(false);
        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        assertBlocked(advance);

        Future<LocalDate> late = executor.submit(() -> tx.execute(status -> gate.acquireSharedAndReadBusinessDate()));
        assertBlocked(late);

        holder.release();
        assertThat(advance.get(10, SECONDS)).isEqualTo(next);
        assertThat(late.get(10, SECONDS)).isEqualTo(next);
        assertThat(holder.result()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("커밋 전 전환 UPDATE 가 있으면 gate 는 대기했다가 커밋된 새 값을 읽는다")
    void gateWaitsForUncommittedAdvanceThenReadsNewValue() throws Exception {
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        Future<?> updater = executor.submit(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE business_date SET business_date = ? WHERE date_type = 'BUSINESS_DATE'", next);
            updated.countDown();
            awaitLatch(commit);
        }));
        awaitLatch(updated);

        Future<LocalDate> reader = executor.submit(() -> tx.execute(status -> gate.acquireSharedAndReadBusinessDate()));
        assertBlocked(reader);

        commit.countDown();
        await(updater);
        assertThat(reader.get(10, SECONDS)).isEqualTo(next);
    }

    @Test
    @DisplayName("gate 를 쥔 트랜잭션이 REQUIRES_NEW 안에서 다시 잡으면 대기 중인 전환 뒤에 서서 풀리지 않는다")
    void nestedAcquireBehindWaitingAdvanceCannotProceed() throws Exception {
        tx.executeWithoutResult(outer -> {
            assertThat(gate.acquireSharedAndReadBusinessDate()).isEqualTo(BASE);
            Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
            assertBlocked(advance);

            long startedAt = System.nanoTime();
            // 바깥이 안쪽을 기다리고, 안쪽은 전환 뒤에 서고, 전환은 바깥을 기다린다 — 락 대기 시간 초과로만 끝난다
            assertThatThrownBy(() -> requiresNewTx.executeWithoutResult(inner -> {
                        jdbc.execute("SET SESSION innodb_lock_wait_timeout = 3");
                        try {
                            gate.acquireSharedAndReadBusinessDate();
                        } finally {
                            jdbc.execute("SET SESSION innodb_lock_wait_timeout = 50");
                        }
                    }))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(System.nanoTime() - startedAt).isGreaterThan(2_000_000_000L);

            outer.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("계좌 락을 먼저 잡고 gate 를 부르면 교착이 나고 롤백되는 쪽은 거래가 아니라 영업일 전환이다")
    void reversedLockOrderIsDetectedAsDeadlock() throws Exception {
        LocalDate holiday = jdbc.queryForObject("SELECT MIN(holiday_date) FROM holiday", LocalDate.class);
        CountDownLatch dateLockHeld = new CountDownLatch(1);
        CountDownLatch accountLockHeld = new CountDownLatch(1);
        CountDownLatch queueReady = new CountDownLatch(1);

        // 올바른 순서: 날짜 락 → 계좌 락
        Future<?> correctOrder = executor.submit(() -> tx.executeWithoutResult(status -> {
            gate.acquireSharedAndReadBusinessDate();
            dateLockHeld.countDown();
            awaitLatch(accountLockHeld);
            awaitLatch(queueReady);
            lockAccountRow(holiday);
        }));
        awaitLatch(dateLockHeld);

        // 뒤집힌 순서: 계좌 락 → 날짜 락
        Future<?> reversedOrder = executor.submit(() -> tx.executeWithoutResult(status -> {
            lockAccountRow(holiday);
            accountLockHeld.countDown();
            awaitLatch(queueReady);
            gate.acquireSharedAndReadBusinessDate();
        }));
        awaitLatch(accountLockHeld);

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        assertBlocked(advance);
        queueReady.countDown();

        // 멈추지 않고 DB 가 교착을 감지한다 — 희생자는 변경량이 가장 적은 영업일 전환 UPDATE 라 거래가 아니라 COB 가 실패한다
        assertThat(correctOrder.get(30, SECONDS)).isNull();
        assertThat(reversedOrder.get(30, SECONDS)).isNull();
        assertThatThrownBy(() -> advance.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PessimisticLockingFailureException.class);
        assertThat(fixture.currentInDb()).isEqualTo(BASE);
    }

    private void lockAccountRow(LocalDate holiday) {
        jdbc.queryForObject(
                "SELECT holiday_name FROM holiday WHERE holiday_date = ? FOR UPDATE", String.class, holiday);
    }

    private final class GateHolder {
        private final CountDownLatch acquired = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean rollback;
        private final Future<LocalDate> result;

        GateHolder(boolean rollback) {
            this.rollback = new AtomicBoolean(rollback);
            this.result = executor.submit(() -> tx.execute(status -> {
                LocalDate businessDate = gate.acquireSharedAndReadBusinessDate();
                acquired.countDown();
                awaitLatch(release);
                if (this.rollback.get()) {
                    status.setRollbackOnly();
                }
                return businessDate;
            }));
            awaitLatch(acquired);
        }

        void release() {
            release.countDown();
        }

        LocalDate result() throws Exception {
            return result.get(10, SECONDS);
        }
    }

    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(700, MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) {
                throw new IllegalStateException("래치 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(Future<?> future) {
        try {
            future.get(10, SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
