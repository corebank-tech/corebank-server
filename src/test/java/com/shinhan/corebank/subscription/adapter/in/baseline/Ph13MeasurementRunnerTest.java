package com.shinhan.corebank.subscription.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.subscription.application.port.out.AccumulatedDailyBalanceSqlPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class Ph13MeasurementRunnerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

    private static final LocalDate TO = LocalDate.of(2026, 10, 1);

    @Mock
    private AccumulatedDailyBalanceSqlPort sqlPort;

    @Test
    void measure_processesAccountsInBatches() {
        Ph13MeasurementProperties properties = new Ph13MeasurementProperties(100L, 3, 2, FROM, TO);

        when(sqlPort.calculate(List.of(100L, 101L), FROM, TO)).thenReturn(Map.of(100L, 100L, 101L, 200L));

        when(sqlPort.calculate(List.of(102L), FROM, TO)).thenReturn(Map.of(102L, 0L));

        Ph13MeasurementRunner runner = new Ph13MeasurementRunner(sqlPort, properties);

        var result = runner.measure();

        assertThat(result.batchCount()).isEqualTo(2);
        assertThat(result.checksum()).isEqualTo(300L);
        assertThat(result.zeroResultCount()).isEqualTo(1);
        assertThat(result.fingerprintSha256()).hasSize(64);

        assertThat(result.totalSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.averageBatchMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.p95BatchMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.p99BatchMs()).isGreaterThanOrEqualTo(0);

        verify(sqlPort).calculate(List.of(100L, 101L), FROM, TO);
        verify(sqlPort).calculate(List.of(102L), FROM, TO);
    }

    @Test
    void measure_detectsDifferentAccountResultsWithSameChecksum() {
        Ph13MeasurementProperties properties = new Ph13MeasurementProperties(100L, 2, 2, FROM, TO);

        List<Long> accountIds = List.of(100L, 101L);

        when(sqlPort.calculate(accountIds, FROM, TO))
                .thenReturn(Map.of(100L, 100L, 101L, 200L), Map.of(100L, 200L, 101L, 100L));

        Ph13MeasurementRunner runner = new Ph13MeasurementRunner(sqlPort, properties);

        var first = runner.measure();
        var second = runner.measure();

        assertThat(first.checksum()).isEqualTo(second.checksum());

        assertThat(first.fingerprintSha256()).isNotEqualTo(second.fingerprintSha256());
    }

    @Test
    void measure_rejectsMissingAccountResult() {
        Ph13MeasurementProperties properties = new Ph13MeasurementProperties(100L, 2, 2, FROM, TO);

        when(sqlPort.calculate(List.of(100L, 101L), FROM, TO)).thenReturn(Map.of(100L, 100L));

        Ph13MeasurementRunner runner = new Ph13MeasurementRunner(sqlPort, properties);

        assertThatThrownBy(runner::measure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accountId=101");
    }

    @Test
    void run_recordsMeasurementResult() {
        Ph13MeasurementProperties properties = new Ph13MeasurementProperties(100L, 1, 1, FROM, TO);

        when(sqlPort.calculate(List.of(100L), FROM, TO)).thenReturn(Map.of(100L, 500L));

        Ph13MeasurementRunner runner = new Ph13MeasurementRunner(sqlPort, properties);

        runner.run(new DefaultApplicationArguments());

        verify(sqlPort).calculate(List.of(100L), FROM, TO);
    }

    @Test
    void measure_rejectsNullSqlResult() {
        Ph13MeasurementProperties properties = new Ph13MeasurementProperties(100L, 1, 1, FROM, TO);

        when(sqlPort.calculate(List.of(100L), FROM, TO)).thenReturn((Map<Long, Long>) null);

        Ph13MeasurementRunner runner = new Ph13MeasurementRunner(sqlPort, properties);

        assertThatThrownBy(runner::measure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQL 집계 결과가 null");
    }
}
