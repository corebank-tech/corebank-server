package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.TestKeys;
import com.shinhan.infoetl.application.port.out.AsOfCalculator;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.GlMovementReader;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.application.port.out.LoadLockPort;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.application.port.out.ReplicaEventPort;
import com.shinhan.infoetl.application.port.out.ReplicaReconciliationPort;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.LedgerMismatchException;
import com.shinhan.infoetl.domain.LoadAlreadyRunningException;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// DB 없이 가짜 포트로 서비스의 흐름만 확인한다. 트랜잭션 롤백은 DailyLoadIntegrationTest가 진짜 DB로 확인한다.
class DailyLoadServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final Pseudonymizer pseudonymizer = new Pseudonymizer(TestKeys.bytes("u"));
    private final FakeMartLoadPort martPort = new FakeMartLoadPort();
    private final FakeLock lockPort = new FakeLock();
    private final FakeCalendar calendar = new FakeCalendar();
    private final FakeReconciliation reconciliation = new FakeReconciliation();

    @Test
    @DisplayName("읽은 행을 가명 키로 바꿔 한 번에 넘기고 결과를 돌려준다")
    void publishesPseudonymizedRows() {
        DailyLoadService service = service(
                List.of(
                        new BalanceRow(1L, 11L, "DEMAND_DEPOSIT", null, null, 300L),
                        new BalanceRow(2L, 12L, "TIME_DEPOSIT", 100L, "DEPOSIT", 700L)),
                List.of());

        LoadResult result = service.load(D);

        assertThat(result.loadBatchId()).isEqualTo(FakeMartLoadPort.BATCH_ID);
        assertThat(martPort.publishedBalances).hasSize(2);
        assertThat(martPort.publishedBalances.get(0))
                .isEqualTo(new MartBalanceRow(
                        pseudonymizer.accountKey(1L),
                        pseudonymizer.customerKey(11L),
                        "DEMAND_DEPOSIT",
                        null,
                        null,
                        300L));
        assertThat(martPort.publishedBalances.get(1).productId()).isEqualTo(100L);
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("이벤트도 가명 키로 바꿔 잔액과 함께 한 번에 넘긴다")
    void publishesPseudonymizedEvents() {
        DailyLoadService service = service(
                oneBalance(),
                List.of(
                        new SubscriptionEventRow(EventType.SUBSCRIBE, 5L, 55L, 100L, "P100", "정기예금", "DEPOSIT"),
                        new SubscriptionEventRow(EventType.CLOSE, 6L, 66L, 100L, "P100", "정기예금", "DEPOSIT")));

        service.load(D);

        assertThat(martPort.publishedEvents)
                .containsExactly(
                        new MartEventRow(
                                EventType.SUBSCRIBE,
                                pseudonymizer.accountKey(5L),
                                pseudonymizer.customerKey(55L),
                                100L,
                                "P100",
                                "정기예금",
                                "DEPOSIT"),
                        new MartEventRow(
                                EventType.CLOSE,
                                pseudonymizer.accountKey(6L),
                                pseudonymizer.customerKey(66L),
                                100L,
                                "P100",
                                "정기예금",
                                "DEPOSIT"));
    }

    @Test
    @DisplayName("이벤트가 0건이어도 정상으로 적재한다")
    void emptyEventsAreNormal() {
        service(oneBalance(), List.of()).load(D);

        assertThat(martPort.publishedBalances).hasSize(1);
        assertThat(martPort.publishedEvents).isEmpty();
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("시작할 때 6시간을 오래된 RUNNING 기준으로 넘긴다")
    void passesStaleThresholdWhenStarting() {
        service(oneBalance(), List.of()).load(D);

        assertThat(martPort.startedStaleAfter).isEqualTo(Duration.ofHours(6));
        assertThat(martPort.startedBaseDate).isEqualTo(D);
    }

    @Test
    @DisplayName("읽은 잔액이 0건이면 마트에 쓰지 않고 실패로 기록한다")
    void emptyBalancesFailWithoutPublishing() {
        DailyLoadService service = service(List.of(), List.of());

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalStateException.class);

        assertThat(martPort.publishedBalances).isNull();
        assertThat(martPort.failedReasons).hasSize(1);
        assertThat(martPort.failedReasons.get(0)).contains("0건");
    }

    @Test
    @DisplayName("레플리카 읽기가 실패하면 마트에 쓰지 않고 실패로 기록하고 예외를 그대로 던진다")
    void replicaFailureIsRecordedAndRethrown() {
        EtlStorageException failure = new EtlStorageException("읽기 실패", new RuntimeException("boom"));
        DailyLoadService service = new DailyLoadService(
                baseDate -> {
                    throw failure;
                },
                baseDate -> List.of(),
                reconciliation,
                calendar,
                martPort,
                lockPort,
                pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isSameAs(failure);

        assertThat(martPort.publishedBalances).isNull();
        assertThat(martPort.failedReasons).hasSize(1);
        assertThat(martPort.failedReasons.get(0)).startsWith("EtlStorageException");
    }

    @Test
    @DisplayName("마트 쓰기가 실패하면 실패로 기록하고 예외를 그대로 던진다")
    void publishFailureIsRecordedAndRethrown() {
        martPort.publishFailure = new EtlStorageException("쓰기 실패", new RuntimeException("boom"));
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(D)).isSameAs(martPort.publishFailure);

        assertThat(martPort.failedReasons).hasSize(1);
    }

    @Test
    @DisplayName("실패 기록까지 실패하면 원래 예외를 던지고 기록 실패는 덧붙인다")
    void recordingFailureDoesNotHideOriginalException() {
        martPort.publishFailure = new EtlStorageException("쓰기 실패", new RuntimeException("boom"));
        martPort.failFailure = new EtlStorageException("기록 실패", new RuntimeException("down"));
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(D))
                .isSameAs(martPort.publishFailure)
                .hasSuppressedException(martPort.failFailure);
    }

    @Test
    @DisplayName("락을 못 얻으면 적재 시작 기록도 남기지 않고 예외를 던진다")
    void lockFailureStopsBeforeStart() {
        lockPort.busy = true;
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(LoadAlreadyRunningException.class);

        assertThat(martPort.startedBaseDate).isNull();
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("적재가 끝나면 성공이든 실패든 락을 놓는다")
    void releasesLockAfterLoad() {
        service(oneBalance(), List.of()).load(D);
        assertThat(lockPort.released).isTrue();

        FakeLock failingLock = new FakeLock();
        martPort.publishFailure = new EtlStorageException("쓰기 실패", new RuntimeException("boom"));
        DailyLoadService failing = new DailyLoadService(
                baseDate -> oneBalance(),
                baseDate -> List.of(),
                reconciliation,
                calendar,
                martPort,
                failingLock,
                pseudonymizer);
        assertThatThrownBy(() -> failing.load(D)).isInstanceOf(EtlStorageException.class);
        assertThat(failingLock.released).isTrue();
    }

    @Test
    @DisplayName("주말을 기준일로 주면 락도 적재 기록도 남기지 않고 거부한다")
    void weekendBaseDateIsRejectedWithoutAnyRecord() {
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(LocalDate.of(2026, 10, 24)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영업일");

        assertThat(martPort.startedBaseDate).isNull();
        assertThat(martPort.publishedBalances).isNull();
        assertThat(martPort.failedReasons).isEmpty();
        assertThat(lockPort.acquired).isFalse();
    }

    @Test
    @DisplayName("공휴일을 기준일로 주면 거부한다")
    void holidayBaseDateIsRejected() {
        calendar.holidays = Set.of(D);
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalArgumentException.class);

        assertThat(martPort.startedBaseDate).isNull();
    }

    @Test
    @DisplayName("이미 COMPLETED인 기준일은 rebuild 없이는 거부하고 적재 기록도 남기지 않는다")
    void completedBaseDateRequiresRebuild() {
        martPort.completedBeforeLoad = Set.of(D);
        DailyLoadService service = service(oneBalance(), List.of());

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalArgumentException.class);

        assertThat(martPort.startedBaseDate).isNull();
    }

    @Test
    @DisplayName("rebuild를 주면 COMPLETED인 기준일도 다시 적재한다")
    void rebuildReloadsCompletedBaseDate() {
        martPort.completedBeforeLoad = Set.of(D);
        DailyLoadService service = service(oneBalance(), List.of());

        service.load(D, true);

        assertThat(martPort.startedBaseDate).isEqualTo(D);
    }

    @Test
    @DisplayName("레플리카에서 읽은 원장 합계를 마트 반영에 넘기고 GL 증감 읽기도 연결한다")
    void passesLedgerTotalAndGlReaderToPublish() {
        reconciliation.ledgerTotal = 300L;
        reconciliation.glMovement = 42L;
        service(oneBalance(), List.of()).load(D);

        assertThat(martPort.publishedLedgerTotal).isEqualTo(300L);
        assertThat(martPort.publishedGlReader.readDepositMovement(D.minusDays(1), D))
                .isEqualTo(42L);
        assertThat(reconciliation.glFrom).isEqualTo(D.minusDays(1));
        assertThat(reconciliation.glTo).isEqualTo(D);
    }

    @Test
    @DisplayName("원장 대조에 실패하면 두 합계와 함께 실패로 기록하고 예외를 그대로 던진다")
    void ledgerMismatchIsRecordedWithTotals() {
        LedgerMismatchException mismatch = new LedgerMismatchException(1_000L, 900L);
        martPort.publishFailure = mismatch;

        assertThatThrownBy(() -> service(oneBalance(), List.of()).load(D)).isSameAs(mismatch);

        assertThat(martPort.mismatchLedgerTotal).isEqualTo(1_000L);
        assertThat(martPort.mismatchMartTotal).isEqualTo(900L);
        assertThat(martPort.mismatchReason).contains("LedgerMismatchException");
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("T2에서 완료된 날짜 범위의 휴일로 asOf를 계산해 결과에 담는다")
    void computesAsOfFromCompletedDatesInsideTheLoad() {
        martPort.completedBeforeLoad = Set.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));
        calendar.holidays = Set.of(LocalDate.of(2026, 10, 19)); // 10-17,18은 주말, 10-19는 공휴일
        DailyLoadService service = service(oneBalance(), List.of());

        LoadResult result = service.load(D);

        assertThat(result.asOfDate()).isEqualTo(D);
        assertThat(calendar.lastRange).containsExactly(LocalDate.of(2026, 10, 15), D);
    }

    private DailyLoadService service(List<BalanceRow> balances, List<SubscriptionEventRow> events) {
        ReplicaBalancePort balancePort = baseDate -> balances;
        ReplicaEventPort eventPort = baseDate -> events;
        return new DailyLoadService(
                balancePort, eventPort, reconciliation, calendar, martPort, lockPort, pseudonymizer);
    }

    private static List<BalanceRow> oneBalance() {
        return List.of(new BalanceRow(1L, 11L, "DEMAND_DEPOSIT", null, null, 300L));
    }

    private static final class FakeReconciliation implements ReplicaReconciliationPort {

        long ledgerTotal = 300L;
        long glMovement = 0L;
        LocalDate glFrom;
        LocalDate glTo;

        @Override
        public long readLedgerTotal(LocalDate baseDate) {
            return ledgerTotal;
        }

        @Override
        public long readGlDepositMovement(LocalDate fromExclusive, LocalDate toInclusive) {
            glFrom = fromExclusive;
            glTo = toInclusive;
            return glMovement;
        }
    }

    private static final class FakeCalendar implements ReplicaCalendarPort {

        Set<LocalDate> holidays = Set.of();
        List<LocalDate> lastRange;

        @Override
        public Set<LocalDate> readHolidays(LocalDate from, LocalDate to) {
            this.lastRange = List.of(from, to);
            return holidays;
        }
    }

    private static final class FakeLock implements LoadLockPort {

        boolean busy;
        boolean acquired;
        boolean released;

        @Override
        public LoadLock acquire(LocalDate baseDate) {
            if (busy) {
                throw new LoadAlreadyRunningException(baseDate);
            }
            acquired = true;
            released = false;
            return () -> released = true;
        }
    }

    private static final class FakeMartLoadPort implements MartLoadPort {

        static final long BATCH_ID = 7L;

        LocalDate startedBaseDate;
        Duration startedStaleAfter;
        List<MartBalanceRow> publishedBalances;
        List<MartEventRow> publishedEvents;
        Long publishedLedgerTotal;
        GlMovementReader publishedGlReader;
        String mismatchReason;
        Long mismatchLedgerTotal;
        Long mismatchMartTotal;
        Set<LocalDate> completedBeforeLoad = Set.of();
        final List<String> failedReasons = new ArrayList<>();
        RuntimeException publishFailure;
        RuntimeException failFailure;

        @Override
        public boolean isCompleted(LocalDate baseDate) {
            return completedBeforeLoad.contains(baseDate);
        }

        @Override
        public long start(LocalDate baseDate, Duration staleAfter) {
            this.startedBaseDate = baseDate;
            this.startedStaleAfter = staleAfter;
            return BATCH_ID;
        }

        @Override
        public LoadResult publish(
                long loadBatchId,
                LocalDate baseDate,
                List<MartBalanceRow> balances,
                List<MartEventRow> events,
                long ledgerTotal,
                GlMovementReader glMovementReader,
                AsOfCalculator asOfCalculator) {
            this.publishedLedgerTotal = ledgerTotal;
            this.publishedGlReader = glMovementReader;
            if (publishFailure != null) {
                throw publishFailure;
            }
            this.publishedBalances = balances;
            this.publishedEvents = events;
            Set<LocalDate> completed = new HashSet<>(completedBeforeLoad);
            completed.add(baseDate);
            return new LoadResult(
                    loadBatchId,
                    baseDate,
                    balances.size(),
                    events.size(),
                    balances.stream().mapToLong(MartBalanceRow::balance).sum(),
                    asOfCalculator.calculate(Collections.min(completed), completed));
        }

        @Override
        public void failLedgerMismatch(long loadBatchId, String reason, long ledgerTotal, long martBalanceTotal) {
            this.mismatchReason = reason;
            this.mismatchLedgerTotal = ledgerTotal;
            this.mismatchMartTotal = martBalanceTotal;
        }

        @Override
        public void fail(long loadBatchId, String reason) {
            failedReasons.add(reason);
            if (failFailure != null) {
                throw failFailure;
            }
        }
    }
}
