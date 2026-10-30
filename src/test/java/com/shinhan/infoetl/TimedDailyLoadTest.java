package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.domain.LoadResult;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimedDailyLoadTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("결과와 인자를 그대로 위임하고 돌려준다")
    void delegatesAndReturnsResult() {
        LoadResult expected = new LoadResult(1L, D, 1L, 0L, 100L, D);
        boolean[] rebuildSeen = new boolean[1];

        LoadResult actual = new TimedDailyLoad((baseDate, rebuild) -> {
                    rebuildSeen[0] = rebuild;
                    return expected;
                })
                .load(D, true);

        assertThat(actual).isSameAs(expected);
        assertThat(rebuildSeen[0]).isTrue();
    }

    @Test
    @DisplayName("적재가 실패하면 예외를 그대로 던진다")
    void rethrowsFailure() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> new TimedDailyLoad((baseDate, rebuild) -> {
                            throw failure;
                        })
                        .load(D, false))
                .isSameAs(failure);
    }
}
