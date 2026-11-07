package com.shinhan.corebank.subscription.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

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

        AccumulatedDailyBalanceBaselineRunner runner = new AccumulatedDailyBalanceBaselineRunner(
                accumulatedDailyBalanceUseCase, properties, new MockEnvironment());

        AccumulatedDailyBalanceBaselineRunner.BaselineResult result = runner.measure();

        assertThat(result.checksum()).isEqualTo(600L);
        assertThat(result.zeroResultCount()).isZero();
        assertThat(result.totalSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.averageMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.p95Ms()).isGreaterThanOrEqualTo(0);
        assertThat(result.p99Ms()).isGreaterThanOrEqualTo(0);
        assertThat(result.fingerprintSha256()).hasSize(64);

        InOrder inOrder = inOrder(accumulatedDailyBalanceUseCase);

        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_001L, fromInclusive, toExclusive);
        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_002L, fromInclusive, toExclusive);
        inOrder.verify(accumulatedDailyBalanceUseCase).calculate(60_000_003L, fromInclusive, toExclusive);
    }

    @Test
    void properties_rejectZeroAccountCount() {
        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 2);

        assertThatThrownBy(
                        () -> new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 0, fromInclusive, toExclusive))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void properties_rejectInvalidDateRange() {
        LocalDate date = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 30_000, date, date))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void measure_countsZeroResults() {
        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 2);

        AccumulatedDailyBalanceBaselineProperties properties =
                new AccumulatedDailyBalanceBaselineProperties(60_000_001L, 2, fromInclusive, toExclusive);

        when(accumulatedDailyBalanceUseCase.calculate(60_000_001L, fromInclusive, toExclusive))
                .thenReturn(100L);
        when(accumulatedDailyBalanceUseCase.calculate(60_000_002L, fromInclusive, toExclusive))
                .thenReturn(0L);

        AccumulatedDailyBalanceBaselineRunner runner = new AccumulatedDailyBalanceBaselineRunner(
                accumulatedDailyBalanceUseCase, properties, new MockEnvironment());

        AccumulatedDailyBalanceBaselineRunner.BaselineResult result = runner.measure();

        assertThat(result.checksum()).isEqualTo(100L);
        assertThat(result.zeroResultCount()).isEqualTo(1);
    }

    @Test
    void run_rejectsSqlEnabledBaseline() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = from.plusDays(1);

        var properties = new AccumulatedDailyBalanceBaselineProperties(100L, 1, from, to);

        var environment = new MockEnvironment().withProperty("app.ph13.sql-enabled", "true");

        var runner = new AccumulatedDailyBalanceBaselineRunner(accumulatedDailyBalanceUseCase, properties, environment);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PH-11");

        verifyNoInteractions(accumulatedDailyBalanceUseCase);
    }

    @Test
    void run_recordsLegacyBaselineWhenSqlDisabled() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = from.plusDays(1);

        var properties = new AccumulatedDailyBalanceBaselineProperties(100L, 1, from, to);

        when(accumulatedDailyBalanceUseCase.calculate(100L, from, to)).thenReturn(500L);

        var runner = new AccumulatedDailyBalanceBaselineRunner(
                accumulatedDailyBalanceUseCase, properties, new MockEnvironment());

        runner.run(new DefaultApplicationArguments());

        verify(accumulatedDailyBalanceUseCase).calculate(100L, from, to);
    }
}
