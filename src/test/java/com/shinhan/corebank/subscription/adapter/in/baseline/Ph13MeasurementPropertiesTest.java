package com.shinhan.corebank.subscription.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Ph13MeasurementPropertiesTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 1);

    @Test
    void acceptsValidBoundaries() {
        var properties = new Ph13MeasurementProperties(100L, 150_000, 1000, FROM, TO);

        assertThat(properties.batchSize()).isEqualTo(1000);
        assertThat(properties.accountCount()).isEqualTo(150_000);
    }

    @Test
    void rejectsInvalidAccountStart() {
        assertInvalid(0L, 1, 1, FROM, TO);
        assertInvalid(-1L, 1, 1, FROM, TO);
    }

    @Test
    void rejectsInvalidAccountCount() {
        assertInvalid(100L, 0, 1, FROM, TO);
        assertInvalid(100L, -1, 1, FROM, TO);
    }

    @Test
    void rejectsInvalidBatchSize() {
        assertInvalid(100L, 1, 0, FROM, TO);
        assertInvalid(100L, 1, 1001, FROM, TO);
    }

    @Test
    void rejectsInvalidDateRange() {
        assertInvalid(100L, 1, 1, null, TO);
        assertInvalid(100L, 1, 1, FROM, null);
        assertInvalid(100L, 1, 1, FROM, FROM);
        assertInvalid(100L, 1, 1, TO, FROM);
    }

    @Test
    void rejectsAccountIdOverflow() {
        assertThatThrownBy(() -> new Ph13MeasurementProperties(Long.MAX_VALUE, 2, 1, FROM, TO))
                .isInstanceOf(ArithmeticException.class);
    }

    private void assertInvalid(long accountIdStart, int accountCount, int batchSize, LocalDate from, LocalDate to) {

        assertThatThrownBy(() -> new Ph13MeasurementProperties(accountIdStart, accountCount, batchSize, from, to))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
