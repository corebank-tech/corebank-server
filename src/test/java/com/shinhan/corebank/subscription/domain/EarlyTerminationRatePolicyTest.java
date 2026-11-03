package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EarlyTerminationRatePolicyTest {

    private final EarlyTerminationRatePolicy policy = new EarlyTerminationRatePolicy(
            List.of(tier(0, "0.10"), tier(30, "0.20"), tier(90, "0.40"), tier(180, "0.60"), tier(365, "0.80")));

    @Test
    void appliesTheSameTierToTheEntireDepositPeriod() {
        LocalDate opened = LocalDate.of(2025, 1, 1);
        assertThat(policy.calculate(opened, opened.plusDays(29), new BigDecimal("3.0")))
                .isEqualByComparingTo("0.300");
        assertThat(policy.calculate(opened, opened.plusDays(30), new BigDecimal("3.0")))
                .isEqualByComparingTo("0.600");
        assertThat(policy.calculate(opened, opened.plusDays(89), new BigDecimal("3.0")))
                .isEqualByComparingTo("0.600");
        assertThat(policy.calculate(opened, opened.plusDays(90), new BigDecimal("3.0")))
                .isEqualByComparingTo("1.200");
        assertThat(policy.calculate(opened, opened.plusDays(179), new BigDecimal("3.0")))
                .isEqualByComparingTo("1.200");
        assertThat(policy.calculate(opened, opened.plusDays(180), new BigDecimal("3.0")))
                .isEqualByComparingTo("1.800");
        assertThat(policy.calculate(opened, opened.plusDays(364), new BigDecimal("3.0")))
                .isEqualByComparingTo("1.800");
        assertThat(policy.calculate(opened, opened.plusDays(365), new BigDecimal("3.0")))
                .isEqualByComparingTo("2.400");
    }

    @Test
    void sameDayTerminationSelectsFirstTierButHasZeroAccruedDays() {
        LocalDate opened = LocalDate.of(2026, 11, 2);
        assertThat(policy.calculate(opened, opened, new BigDecimal("3.0"))).isEqualByComparingTo("0.3");
    }

    @Test
    void rejectsInvalidRatesAndDates() {
        LocalDate opened = LocalDate.of(2026, 11, 2);
        assertThatThrownBy(() -> policy.calculate(opened, opened.minusDays(1), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.calculate(opened, opened, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOrDuplicateTiers() {
        assertThatThrownBy(() -> new EarlyTerminationRatePolicy(List.of(tier(30, "0.20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EarlyTerminationRatePolicy(List.of(tier(0, "0.10"), tier(0, "0.20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tier(10, "1.01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotRoundEffectiveRateBeforeInterestCalculation() {
        LocalDate opened = LocalDate.of(2026, 1, 1);

        BigDecimal effectiveRate = policy.calculate(opened, opened.plusDays(29), new BigDecimal("3.375"));

        assertThat(effectiveRate).isEqualByComparingTo("0.33750");
    }

    private static EarlyTerminationRatePolicy.Tier tier(long days, String multiplier) {
        return new EarlyTerminationRatePolicy.Tier(days, new BigDecimal(multiplier));
    }
}
