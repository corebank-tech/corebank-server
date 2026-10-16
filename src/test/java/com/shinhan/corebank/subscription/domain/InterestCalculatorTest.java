package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestCalculatorTest {

    private static final InterestCalculator.TaxRates TAX_RATES =
            new InterestCalculator.TaxRates(new BigDecimal("0.14"), new BigDecimal("0.10"));

    @Test
    @DisplayName("단리: 실제 적수와 연 3.70%로 계산하고 원 단위 절사한다")
    void calculateSimple_truncatesToWon() {
        InterestResult result = InterestCalculator.calculateSimple(5_000_000L, new BigDecimal("3.70"), TAX_RATES);

        assertThat(result.gross()).isEqualTo(506L);
        assertThat(result.tax()).isEqualTo(70L);
        assertThat(result.net()).isEqualTo(436L);
    }

    @Test
    @DisplayName("월복리: 매월 절사한 이자를 다음 월 계산에 반영한다")
    void calculateMonthlyCompound_addsPreviousInterest() {
        List<InterestCalculator.MonthlyPeriod> periods = List.of(
                new InterestCalculator.MonthlyPeriod(28_000_000L, 28),
                new InterestCalculator.MonthlyPeriod(31_000_000L, 31),
                new InterestCalculator.MonthlyPeriod(30_000_000L, 30));

        InterestResult result = InterestCalculator.calculateMonthlyCompound(periods, new BigDecimal("3.70"), TAX_RATES);

        assertThat(result.gross()).isEqualTo(9_048L);
        assertThat(result.tax()).isEqualTo(1_380L);
        assertThat(result.net()).isEqualTo(7_668L);
    }

    @Test
    @DisplayName("세금: 소득세와 지방소득세를 각각 10원 미만 절사한다")
    void calculateSimple_truncatesEachTax() {
        // 365일 적수에 연 100%를 적용해 세전이자 123,457원을 만든다.
        InterestResult result = InterestCalculator.calculateSimple(123_457L * 365, new BigDecimal("100"), TAX_RATES);

        assertThat(result.gross()).isEqualTo(123_457L);
        assertThat(result.tax()).isEqualTo(19_000L);
        assertThat(result.net()).isEqualTo(104_457L);
    }

    @Test
    @DisplayName("적수가 0이면 세전·세금·세후이자가 모두 0원이다")
    void calculateSimple_zeroBalance() {
        InterestResult result = InterestCalculator.calculateSimple(0L, new BigDecimal("3.70"), TAX_RATES);

        assertThat(result.gross()).isZero();
        assertThat(result.tax()).isZero();
        assertThat(result.net()).isZero();
    }

    @Test
    @DisplayName("음수 적수는 INVALID_INPUT으로 거부한다")
    void calculateSimple_rejectsNegativeBalance() {
        assertThatThrownBy(() -> InterestCalculator.calculateSimple(-1L, new BigDecimal("3.70"), TAX_RATES))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("윤년에도 이자 계산 분모는 365를 사용한다")
    void calculateSimple_leapYearUses365Days() {
        // 2024년은 366일
        // 원금 100만원 × 366일 = 적수 366,000,000
        InterestResult result = InterestCalculator.calculateSimple(366_000_000L, new BigDecimal("3.65"), TAX_RATES);

        assertThat(result.gross()).isEqualTo(36_600L);
    }

    @Test
    @DisplayName("세금 10원 절사 경계에서 소득세와 지방소득세를 각각 계산한다")
    void calculateSimple_taxRoundingBoundary() {
        InterestResult below = InterestCalculator.calculateSimple(714L * 365, new BigDecimal("100"), TAX_RATES);

        InterestResult above = InterestCalculator.calculateSimple(715L * 365, new BigDecimal("100"), TAX_RATES);

        // 714원의 소득세 99.96원 → 90원
        // 지방소득세 9원 → 0원
        assertThat(below.tax()).isEqualTo(90L);

        // 715원의 소득세 100.1원 → 100원
        // 지방소득세 10원
        assertThat(above.tax()).isEqualTo(110L);
        assertThat(above.net()).isEqualTo(605L);
    }

    @Test
    @DisplayName("이자 계산 결과가 long 범위를 초과하면 INTERNAL_ERROR로 처리한다")
    void calculateSimple_overflow() {
        assertThatThrownBy(
                        () -> InterestCalculator.calculateSimple(Long.MAX_VALUE, new BigDecimal("100000"), TAX_RATES))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_ERROR));
    }
}
