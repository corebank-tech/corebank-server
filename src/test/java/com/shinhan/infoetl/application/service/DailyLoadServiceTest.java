package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.TestKeys;
import com.shinhan.infoetl.application.port.out.AsOfCalculator;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
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
    private final FakeCalendar calendar = new FakeCalendar();

    @Test
    @DisplayName("읽은 행을 가명 키로 바꿔 한 번에 넘기고 결과를 돌려준다")
    void publishesPseudonymizedRows() {
        FakeReplica replica = new FakeReplica(List.of(
                new BalanceRow(1L, 11L, "DEMAND_DEPOSIT", null, null, 300L),
                new BalanceRow(2L, 12L, "TIME_DEPOSIT", 100L, "DEPOSIT", 700L)));
        DailyLoadService service = new DailyLoadService(replica, calendar, martPort, pseudonymizer);

        LoadResult result = service.load(D);

        assertThat(result.loadBatchId()).isEqualTo(FakeMartLoadPort.BATCH_ID);
        assertThat(martPort.published).hasSize(2);
        assertThat(martPort.published.get(0))
                .isEqualTo(new MartBalanceRow(
                        pseudonymizer.accountKey(1L),
                        pseudonymizer.customerKey(11L),
                        "DEMAND_DEPOSIT",
                        null,
                        null,
                        300L));
        assertThat(martPort.published.get(1).productId()).isEqualTo(100L);
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("시작할 때 6시간을 오래된 RUNNING 기준으로 넘긴다")
    void passesStaleThresholdWhenStarting() {
        new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer).load(D);

        assertThat(martPort.startedStaleAfter).isEqualTo(Duration.ofHours(6));
        assertThat(martPort.startedBaseDate).isEqualTo(D);
    }

    @Test
    @DisplayName("읽은 결과가 0건이면 마트에 쓰지 않고 실패로 기록한다")
    void emptyReadFailsWithoutPublishing() {
        DailyLoadService service = new DailyLoadService(new FakeReplica(List.of()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalStateException.class);

        assertThat(martPort.published).isNull();
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
                calendar,
                martPort,
                pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isSameAs(failure);

        assertThat(martPort.published).isNull();
        assertThat(martPort.failedReasons).hasSize(1);
        assertThat(martPort.failedReasons.get(0)).startsWith("EtlStorageException");
    }

    @Test
    @DisplayName("마트 쓰기가 실패하면 실패로 기록하고 예외를 그대로 던진다")
    void publishFailureIsRecordedAndRethrown() {
        martPort.publishFailure = new EtlStorageException("쓰기 실패", new RuntimeException("boom"));
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isSameAs(martPort.publishFailure);

        assertThat(martPort.failedReasons).hasSize(1);
    }

    @Test
    @DisplayName("실패 기록까지 실패하면 원래 예외를 던지고 기록 실패는 덧붙인다")
    void recordingFailureDoesNotHideOriginalException() {
        martPort.publishFailure = new EtlStorageException("쓰기 실패", new RuntimeException("boom"));
        martPort.failFailure = new EtlStorageException("기록 실패", new RuntimeException("down"));
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(D))
                .isSameAs(martPort.publishFailure)
                .hasSuppressedException(martPort.failFailure);
    }

    @Test
    @DisplayName("주말을 기준일로 주면 적재 기록도 남기지 않고 거부한다")
    void weekendBaseDateIsRejectedWithoutAnyRecord() {
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(LocalDate.of(2026, 10, 24)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영업일");

        assertThat(martPort.startedBaseDate).isNull();
        assertThat(martPort.published).isNull();
        assertThat(martPort.failedReasons).isEmpty();
    }

    @Test
    @DisplayName("공휴일을 기준일로 주면 거부한다")
    void holidayBaseDateIsRejected() {
        calendar.holidays = Set.of(D);
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalArgumentException.class);

        assertThat(martPort.startedBaseDate).isNull();
    }

    @Test
    @DisplayName("이미 COMPLETED인 기준일은 rebuild 없이는 거부하고 적재 기록도 남기지 않는다")
    void completedBaseDateRequiresRebuild() {
        martPort.completedBeforeLoad = Set.of(D);
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalArgumentException.class);

        assertThat(martPort.startedBaseDate).isNull();
    }

    @Test
    @DisplayName("rebuild를 주면 COMPLETED인 기준일도 다시 적재한다")
    void rebuildReloadsCompletedBaseDate() {
        martPort.completedBeforeLoad = Set.of(D);
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        service.load(D, true);

        assertThat(martPort.startedBaseDate).isEqualTo(D);
    }

    @Test
    @DisplayName("T2에서 완료된 날짜 범위의 휴일로 asOf를 계산해 결과에 담는다")
    void computesAsOfFromCompletedDatesInsideTheLoad() {
        martPort.completedBeforeLoad = Set.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));
        calendar.holidays = Set.of(LocalDate.of(2026, 10, 19)); // 10-17,18은 주말, 10-19는 공휴일
        DailyLoadService service = new DailyLoadService(new FakeReplica(oneRow()), calendar, martPort, pseudonymizer);

        LoadResult result = service.load(D);

        assertThat(result.asOfDate()).isEqualTo(D);
        assertThat(calendar.lastRange).containsExactly(LocalDate.of(2026, 10, 15), D);
    }

    private static List<BalanceRow> oneRow() {
        return List.of(new BalanceRow(1L, 11L, "DEMAND_DEPOSIT", null, null, 300L));
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

    private record FakeReplica(List<BalanceRow> rows) implements ReplicaBalancePort {
        @Override
        public List<BalanceRow> readBalances(LocalDate baseDate) {
            return rows;
        }
    }

    private static final class FakeMartLoadPort implements MartLoadPort {

        static final long BATCH_ID = 7L;

        LocalDate startedBaseDate;
        Duration startedStaleAfter;
        List<MartBalanceRow> published;
        final List<String> failedReasons = new ArrayList<>();
        RuntimeException publishFailure;
        RuntimeException failFailure;
        Set<LocalDate> completedBeforeLoad = Set.of();

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
                long loadBatchId, LocalDate baseDate, List<MartBalanceRow> rows, AsOfCalculator asOfCalculator) {
            if (publishFailure != null) {
                throw publishFailure;
            }
            this.published = rows;
            Set<LocalDate> completed = new HashSet<>(completedBeforeLoad);
            completed.add(baseDate);
            return new LoadResult(
                    loadBatchId,
                    baseDate,
                    rows.size(),
                    rows.stream().mapToLong(MartBalanceRow::balance).sum(),
                    asOfCalculator.calculate(Collections.min(completed), completed));
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
