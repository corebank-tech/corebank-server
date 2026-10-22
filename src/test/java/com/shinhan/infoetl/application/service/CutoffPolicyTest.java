package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import com.shinhan.infoetl.domain.CutoffMissingException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CutoffPolicyTest {

    private static final LocalDate BOUNDARY = LocalDate.of(2026, 10, 23);

    private final FakeCutoff cutoff = new FakeCutoff();

    @Test
    @DisplayName("경계일을 정하지 않으면 cutoff를 확인하지도 않고 통과한다")
    void disabledNeverChecks() {
        CutoffPolicy policy = CutoffPolicy.disabled();

        assertThatCode(() -> policy.requireStartable(LocalDate.of(2026, 12, 1))).doesNotThrowAnyException();
        assertThat(cutoff.lookups).isZero();
    }

    @Test
    @DisplayName("경계일보다 앞선 기준일은 cutoff가 없어도 통과하고 조회하지도 않는다")
    void beforeBoundaryPassesWithoutLookup() {
        CutoffPolicy policy = CutoffPolicy.enabled(BOUNDARY, cutoff);

        assertThatCode(() -> policy.requireStartable(BOUNDARY.minusDays(1))).doesNotThrowAnyException();
        assertThat(cutoff.lookups).isZero();
    }

    @Test
    @DisplayName("경계일 당일부터는 cutoff가 없으면 COB 전환 실패로 보고 거부한다")
    void boundaryDayAndAfterNeedCutoff() {
        CutoffPolicy policy = CutoffPolicy.enabled(BOUNDARY, cutoff);

        assertThatThrownBy(() -> policy.requireStartable(BOUNDARY))
                .isInstanceOf(CutoffMissingException.class)
                .hasMessageContaining("2026-10-23");
        assertThatThrownBy(() -> policy.requireStartable(BOUNDARY.plusDays(3)))
                .isInstanceOf(CutoffMissingException.class);
    }

    @Test
    @DisplayName("경계일 이후라도 cutoff(CLOSED)가 보이면 통과한다")
    void closedCutoffPasses() {
        cutoff.closed.add(BOUNDARY);
        CutoffPolicy policy = CutoffPolicy.enabled(BOUNDARY, cutoff);

        assertThatCode(() -> policy.requireStartable(BOUNDARY)).doesNotThrowAnyException();
    }

    private static final class FakeCutoff implements ReplicaCutoffPort {

        final Set<LocalDate> closed = new HashSet<>();
        int lookups;

        @Override
        public boolean isClosed(LocalDate baseDate) {
            lookups++;
            return closed.contains(baseDate);
        }

        @Override
        public Optional<LocalDate> latestClosedDate() {
            return closed.stream().max(LocalDate::compareTo);
        }
    }
}
