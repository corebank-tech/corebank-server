package com.shinhan.corebank.subscription.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccumulatedDailyBalanceBaselineRunnerTest {

    @Mock
    private AccumulatedDailyBalanceUseCase accumulatedDailyBalanceUseCase;

    @Test
    void measure_callsAccountsInOrderAndCalculatesChecksum() {

        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 2);

        AccumulatedDailyBalanceBaselineProperties properties =
                new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 3, fromInclusive, toExclusive);

        when(accumulatedDailyBalanceUseCase.calculate(60_000_001L, fromInclusive, toExclusive))
                .thenReturn(100L);
        when(accumulatedDailyBalanceUseCase.calculate(60_000_002L, fromInclusive, toExclusive))
                .thenReturn(200L);
        when(accumulatedDailyBalanceUseCase.calculate(60_000_003L, fromInclusive, toExclusive))
                .thenReturn(300L);

        AccumulatedDailyBalanceBaselineRunner runner =
                new AccumulatedDailyBalanceBaselineRunner(accumulatedDailyBalanceUseCase, properties);

        AccumulatedDailyBalanceBaselineRunner.BaselineResult result = runner.measure();

        assertThat(result.checksum()).isEqualTo(600L);
        assertThat(result.totalSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.averageMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.p95Ms()).isGreaterThanOrEqualTo(0);
        assertThat(result.p99Ms()).isGreaterThanOrEqualTo(0);

        InOrder inOrder = inOrder(accumulatedDailyBalanceUseCase);

        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_001L, fromInclusive, toExclusive);
        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_002L, fromInclusive, toExclusive);
        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_003L, fromInclusive, toExclusive);
    }

    @Test
    void properties_rejectZeroAccountCount() {
        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 2);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 0, fromInclusive, toExclusive))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void properties_rejectInvalidDateRange() {
        LocalDate date = LocalDate.of(2026, 9, 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 30_000, date, date))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
