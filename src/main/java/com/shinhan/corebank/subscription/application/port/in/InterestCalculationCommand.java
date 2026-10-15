package com.shinhan.corebank.subscription.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InterestCalculationCommand(
        Long accountId, LocalDate openedDate, LocalDate toExclusive, BigDecimal appliedRate, CalculationMode mode) {

    public enum CalculationMode {
        SIMPLE,
        MONTHLY_COMPOUND
    }
}
