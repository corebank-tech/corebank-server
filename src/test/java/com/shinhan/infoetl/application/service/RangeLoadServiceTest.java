package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.out.AsOfCalculator;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.GlMovementReader;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.domain.FailedDay;
import com.shinhan.infoetl.domain.LoadAlreadyRunningException;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RangeLoadServiceTest {

    // 2026-10-16(금) ~ 2026-10-22(목). 17·18은 토·일이고, 19(월)은 공휴일로 둔다.
    private static final LocalDate FRI = LocalDate.of(2026, 10, 16);
    private static final LocalDate SAT = LocalDate.of(2026, 10, 17);
    private static final LocalDate SUN = LocalDate.of(2026, 10, 18);
    private static final LocalDate MON_HOLIDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate TUE = LocalDate.of(2026, 10, 20);
    private static final LocalDate WED = LocalDate.of(2026, 10, 21);

    private final RecordingDailyLoad dailyLoad = new RecordingDailyLoad();

    @Test
    @DisplayName("주말과 공휴일은 건너뛰고 영업일만 오래된 날짜부터 적재한다")
    void loadsOnlyBusinessDaysInOrder() {
        RangeLoadService service =
                new RangeLoadService(dailyLoad, (from, to) -> Set.of(MON_HOLIDAY), completed(Set.of()));

        RangeLoadResult result = service.loadRange(FRI, WED);

        assertThat(dailyLoad.loadedDays).containsExactly(FRI, TUE, WED);
        assertThat(result.loaded()).extracting(LoadResult::baseDate).containsExactly(FRI, TUE, WED);
        assertThat(result.hasFailures()).isFalse();
        assertThat(dailyLoad.loadedDays).doesNotContain(SAT, SUN, MON_HOLIDAY);
    }

    @Test
    @DisplayName("한 날짜가 실패해도 다음 날짜를 계속 적재하고 실패한 날짜를 사유와 함께 모은다")
    void continuesAfterFailure() {
        dailyLoad.failures.put(TUE, new EtlStorageException("마트 적재에 실패했습니다", new RuntimeException("boom")));
        RangeLoadService service =
                new RangeLoadService(dailyLoad, (from, to) -> Set.of(MON_HOLIDAY), completed(Set.of()));

        RangeLoadResult result = service.loadRange(FRI, WED);

        assertThat(dailyLoad.loadedDays).containsExactly(FRI, TUE, WED);
        assertThat(result.loaded()).extracting(LoadResult::baseDate).containsExactly(FRI, WED);
        assertThat(result.failed()).extracting(FailedDay::baseDate).containsExactly(TUE);
        assertThat(result.failed().get(0).reason())
                .startsWith("EtlStorageException")
                .contains("마트 적재에 실패했습니다");
        assertThat(result.hasFailures()).isTrue();
    }

    @Test
    @DisplayName("다른 프로세스가 적재 중인 날짜도 실패로 모으고 계속 진행한다")
    void collectsLockedDayAsFailure() {
        dailyLoad.failures.put(WED, new LoadAlreadyRunningException(WED));
        RangeLoadService service = new RangeLoadService(dailyLoad, (from, to) -> Set.of(), completed(Set.of()));

        RangeLoadResult result = service.loadRange(TUE, WED);

        assertThat(result.loaded()).extracting(LoadResult::baseDate).containsExactly(TUE);
        assertThat(result.failed().get(0).reason()).startsWith("LoadAlreadyRunningException");
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 거부한다")
    void rejectsReversedRange() {
        RangeLoadService service = new RangeLoadService(dailyLoad, (from, to) -> Set.of(), completed(Set.of()));

        assertThatThrownBy(() -> service.loadRange(WED, TUE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dailyLoad.loadedDays).isEmpty();
    }

    @Test
    @DisplayName("하루짜리 범위도 영업일이면 적재한다")
    void singleDayRange() {
        RangeLoadService service = new RangeLoadService(dailyLoad, (from, to) -> Set.of(), completed(Set.of()));

        RangeLoadResult result = service.loadRange(TUE, TUE);

        assertThat(result.loaded()).hasSize(1);
    }

    @Test
    @DisplayName("범위 전체가 주말이면 아무것도 적재하지 않고 실패도 아니다")
    void weekendOnlyRangeLoadsNothing() {
        RangeLoadService service = new RangeLoadService(dailyLoad, (from, to) -> Set.of(), completed(Set.of()));

        RangeLoadResult result = service.loadRange(SAT, SUN);

        assertThat(result.loaded()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("이미 COMPLETED인 영업일은 다시 적재하지 않고 건너뛴 날짜로 모은다")
    void skipsCompletedDays() {
        RangeLoadService service = new RangeLoadService(dailyLoad, (from, to) -> Set.of(), completed(Set.of(TUE)));

        RangeLoadResult result = service.loadRange(TUE, WED);

        assertThat(dailyLoad.loadedDays).containsExactly(WED);
        assertThat(result.skipped()).containsExactly(TUE);
        assertThat(result.loaded()).extracting(LoadResult::baseDate).containsExactly(WED);
        assertThat(result.hasFailures()).isFalse();
    }

    private static MartLoadPort completed(Set<LocalDate> completedDays) {
        return new MartLoadPort() {
            @Override
            public boolean isCompleted(LocalDate baseDate) {
                return completedDays.contains(baseDate);
            }

            @Override
            public long start(LocalDate baseDate, Duration staleAfter) {
                throw new UnsupportedOperationException();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public void failLedgerMismatch(long loadBatchId, String reason, long ledgerTotal, long martBalanceTotal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void fail(long loadBatchId, String reason) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class RecordingDailyLoad implements DailyLoadUseCase {

        final List<LocalDate> loadedDays = new ArrayList<>();
        final Map<LocalDate, RuntimeException> failures = new HashMap<>();

        @Override
        public LoadResult load(LocalDate baseDate, boolean rebuild) {
            loadedDays.add(baseDate);
            RuntimeException failure = failures.get(baseDate);
            if (failure != null) {
                throw failure;
            }
            return new LoadResult(1L, baseDate, 1L, 0L, 100L, baseDate);
        }
    }
}
