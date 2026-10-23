package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoLoadServiceTest {

    private static final LocalDate BOUNDARY = LocalDate.of(2026, 10, 23);

    private final RecordingRange range = new RecordingRange();

    @Test
    @DisplayName("cutoff가 하나도 없으면 적재하지 않고 빈 결과를 돌려준다")
    void nothingToLoadWithoutCutoff() {
        RangeLoadResult result = new AutoLoadService(range, cutoff(null), BOUNDARY).loadPending();

        assertThat(range.calls).isEmpty();
        assertThat(result.loaded()).isEmpty();
        assertThat(result.skipped()).isEmpty();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("가장 최근 cutoff가 경계일보다 앞서면 적재하지 않는다")
    void latestCutoffBeforeBoundaryLoadsNothing() {
        new AutoLoadService(range, cutoff(BOUNDARY.minusDays(1)), BOUNDARY).loadPending();

        assertThat(range.calls).isEmpty();
    }

    @Test
    @DisplayName("경계일부터 가장 최근 cutoff까지를 한 번의 기간 적재로 넘기고 결과를 그대로 돌려준다")
    void loadsFromBoundaryToLatestCutoff() {
        LocalDate latest = BOUNDARY.plusDays(4);

        RangeLoadResult result = new AutoLoadService(range, cutoff(latest), BOUNDARY).loadPending();

        assertThat(range.calls).containsExactly(List.of(BOUNDARY, latest));
        assertThat(result).isSameAs(range.result);
    }

    @Test
    @DisplayName("가장 최근 cutoff가 경계일 당일이면 그 하루만 적재한다")
    void latestCutoffOnBoundaryLoadsOneDay() {
        new AutoLoadService(range, cutoff(BOUNDARY), BOUNDARY).loadPending();

        assertThat(range.calls).containsExactly(List.of(BOUNDARY, BOUNDARY));
    }

    private static ReplicaCutoffPort cutoff(LocalDate latest) {
        return new ReplicaCutoffPort() {
            @Override
            public boolean isClosed(LocalDate baseDate) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<LocalDate> latestClosedDate() {
                return Optional.ofNullable(latest);
            }
        };
    }

    private static final class RecordingRange implements RangeLoadUseCase {

        final List<List<LocalDate>> calls = new ArrayList<>();
        final RangeLoadResult result = new RangeLoadResult(List.of(), List.of(), List.of());

        @Override
        public RangeLoadResult loadRange(LocalDate from, LocalDate to) {
            calls.add(List.of(from, to));
            return result;
        }
    }
}
