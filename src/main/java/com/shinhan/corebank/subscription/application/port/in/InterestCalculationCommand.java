package com.shinhan.corebank.subscription.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 이자 계산 요청.
 *
 * @param appliedRate 연이율의 퍼센트 값.
 *                    예: 3.70은 연 3.70%, 0.10은 연 0.10%를 의미한다.
 */
public record InterestCalculationCommand(
        Long accountId, LocalDate openedDate, LocalDate toExclusive, BigDecimal appliedRate, CalculationMode mode) {

    public enum CalculationMode {
        SIMPLE,
        MONTHLY_COMPOUND
    }
}
