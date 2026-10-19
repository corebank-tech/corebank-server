package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.application.port.out.LoadLockPort;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaEventPort;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.LoadAlreadyRunningException;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// DB 없이 가짜 포트로 서비스의 흐름만 확인한다. 트랜잭션 롤백은 DailyLoadIntegrationTest가 진짜 DB로 확인한다.
class DailyLoadServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final Pseudonymizer pseudonymizer =
            new Pseudonymizer("service-unit-key-0001-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final FakeMartLoadPort martPort = new FakeMartLoadPort();
    private final FakeLock lockPort = new FakeLock();

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
                baseDate -> oneBalance(), baseDate -> List.of(), martPort, failingLock, pseudonymizer);
        assertThatThrownBy(() -> failing.load(D)).isInstanceOf(EtlStorageException.class);
        assertThat(failingLock.released).isTrue();
    }

    private DailyLoadService service(List<BalanceRow> balances, List<SubscriptionEventRow> events) {
        ReplicaBalancePort balancePort = baseDate -> balances;
        ReplicaEventPort eventPort = baseDate -> events;
        return new DailyLoadService(balancePort, eventPort, martPort, lockPort, pseudonymizer);
    }

    private static List<BalanceRow> oneBalance() {
        return List.of(new BalanceRow(1L, 11L, "DEMAND_DEPOSIT", null, null, 300L));
    }

    private static final class FakeLock implements LoadLockPort {

        boolean busy;
        boolean released;

        @Override
        public LoadLock acquire(LocalDate baseDate) {
            if (busy) {
                throw new LoadAlreadyRunningException(baseDate);
            }
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
        final List<String> failedReasons = new ArrayList<>();
        RuntimeException publishFailure;
        RuntimeException failFailure;

        @Override
        public long start(LocalDate baseDate, Duration staleAfter) {
            this.startedBaseDate = baseDate;
            this.startedStaleAfter = staleAfter;
            return BATCH_ID;
        }

        @Override
        public LoadResult publish(
                long loadBatchId, LocalDate baseDate, List<MartBalanceRow> balances, List<MartEventRow> events) {
            if (publishFailure != null) {
                throw publishFailure;
            }
            this.publishedBalances = balances;
            this.publishedEvents = events;
            return new LoadResult(
                    loadBatchId,
                    baseDate,
                    balances.size(),
                    events.size(),
                    balances.stream().mapToLong(MartBalanceRow::balance).sum());
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
