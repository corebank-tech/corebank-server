package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccumulatedDailyBalanceCalculatorTest {

    @Test
    @DisplayName("기간 동안 거래가 없으면 시작 잔액을 매일 이월한다")
    void calculate_constantBalance() {
        long result = AccumulatedDailyBalanceCalculator.calculate(
                1_000L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), List.of());

        // 9/1 = 1,000
        // 9/2 = 1,000
        // 9/3 = 1,000
        assertThat(result).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("기간 중 입금이 발생하면 해당 날짜의 일말잔액부터 반영한다")
    void calculate_depositInMiddle() {
        List<LedgerBalancePoint> points =
                List.of(point(LocalDate.of(2026, 9, 2), LocalDateTime.of(2026, 9, 2, 12, 0), 1L, 1_500L));

        long result = AccumulatedDailyBalanceCalculator.calculate(
                1_000L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), points);

        // 9/1 = 1,000
        // 9/2 = 1,500
        // 9/3 = 1,500
        assertThat(result).isEqualTo(4_000L);
    }

    @Test
    @DisplayName("기간 중 출금이 발생하면 해당 날짜의 일말잔액부터 반영한다")
    void calculate_withdrawalInMiddle() {
        List<LedgerBalancePoint> points =
                List.of(point(LocalDate.of(2026, 9, 2), LocalDateTime.of(2026, 9, 2, 12, 0), 1L, 600L));

        long result = AccumulatedDailyBalanceCalculator.calculate(
                1_000L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), points);

        // 9/1 = 1,000
        // 9/2 = 600
        // 9/3 = 600
        assertThat(result).isEqualTo(2_200L);
    }

    @Test
    @DisplayName("같은 날 거래가 여러 건이면 마지막 원장의 잔액을 일말잔액으로 사용한다")
    void calculate_usesLastBalanceOfDay() {
        List<LedgerBalancePoint> points = List.of(
                point(LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 9, 0), 1L, 1_000L),
                point(LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 13, 0), 2L, 1_500L),
                point(LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 18, 0), 3L, 900L));

        long result = AccumulatedDailyBalanceCalculator.calculate(
                0L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), points);

        assertThat(result).isEqualTo(900L);
    }

    @Test
    @DisplayName("거래가 없는 날짜에는 직전 일말잔액을 이월한다")
    void calculate_carriesPreviousBalance() {
        List<LedgerBalancePoint> points = List.of(
                point(LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 10, 0), 1L, 1_000L),
                point(LocalDate.of(2026, 9, 3), LocalDateTime.of(2026, 9, 3, 10, 0), 2L, 1_200L));

        long result = AccumulatedDailyBalanceCalculator.calculate(
                0L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), points);

        // 9/1 = 1,000
        // 9/2 = 1,000
        // 9/3 = 1,200
        assertThat(result).isEqualTo(3_200L);
    }

    @Test
    @DisplayName("발생시각이 같으면 ledgerEntryId가 큰 원장을 마지막 원장으로 사용한다")
    void calculate_usesLedgerEntryIdAsTieBreaker() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 1, 12, 0);

        List<LedgerBalancePoint> points = List.of(
                point(LocalDate.of(2026, 9, 1), occurredAt, 10L, 1_000L),
                point(LocalDate.of(2026, 9, 1), occurredAt, 11L, 1_300L));

        long result = AccumulatedDailyBalanceCalculator.calculate(
                0L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), points);

        assertThat(result).isEqualTo(1_300L);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 INVALID_DATE_RANGE 예외가 발생한다")
    void calculate_invalidDateRange() {
        assertThatThrownBy(() -> AccumulatedDailyBalanceCalculator.calculate(
                        1_000L, LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1), List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;

                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_DATE_RANGE);
                });
    }

    @Test
    @DisplayName("필수 입력값이 없으면 REQUIRED_FIELD_MISSING 예외가 발생한다")
    void calculate_missingRequiredField() {
        assertThatThrownBy(() ->
                        AccumulatedDailyBalanceCalculator.calculate(1_000L, null, LocalDate.of(2026, 9, 4), List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;

                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
                });
    }

    private LedgerBalancePoint point(LocalDate date, LocalDateTime occurredAt, long ledgerEntryId, long balanceAfter) {

        return new LedgerBalancePoint(date, occurredAt, ledgerEntryId, balanceAfter);
    }
}
