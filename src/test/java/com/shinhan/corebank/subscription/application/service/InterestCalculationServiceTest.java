package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand.CalculationMode;
import com.shinhan.corebank.subscription.domain.InterestResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterestCalculationServiceTest {

    @Mock
    private AccumulatedDailyBalanceUseCase balanceUseCase;

    private InterestCalculationService service;

    @BeforeEach
    void setUp() {
        service = new InterestCalculationService(balanceUseCase, new BigDecimal("0.14"), new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("단리: PH-11 적수를 조회해 세전·세후이자를 계산한다")
    void calculateSimple() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 6);

        when(balanceUseCase.calculate(101L, start, end)).thenReturn(5_000_000L);

        InterestResult result = service.calculate(
                new InterestCalculationCommand(101L, start, end, new BigDecimal("3.70"), CalculationMode.SIMPLE));

        assertThat(result.gross()).isEqualTo(506L);
        assertThat(result.tax()).isEqualTo(70L);
        assertThat(result.net()).isEqualTo(436L);

        verify(balanceUseCase).calculate(101L, start, end);
    }

    @Test
    @DisplayName("월복리: 1월 31일 가입 시 최초 가입일 기준 월별 적수를 조회한다")
    void calculateMonthlyCompound_monthEnd() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        LocalDate mar31 = LocalDate.of(2026, 3, 31);
        LocalDate apr30 = LocalDate.of(2026, 4, 30);

        when(balanceUseCase.calculate(101L, jan31, feb28)).thenReturn(28_000_000L);
        when(balanceUseCase.calculate(101L, feb28, mar31)).thenReturn(31_000_000L);
        when(balanceUseCase.calculate(101L, mar31, apr30)).thenReturn(30_000_000L);

        InterestResult result = service.calculate(new InterestCalculationCommand(
                101L, jan31, apr30, new BigDecimal("3.70"), CalculationMode.MONTHLY_COMPOUND));

        assertThat(result.gross()).isEqualTo(9_048L);
        assertThat(result.tax()).isEqualTo(1_380L);
        assertThat(result.net()).isEqualTo(7_668L);

        verify(balanceUseCase).calculate(101L, jan31, feb28);
        verify(balanceUseCase).calculate(101L, feb28, mar31);
        verify(balanceUseCase).calculate(101L, mar31, apr30);
    }

    @Test
    @DisplayName("중도해지: 외부에서 전달한 중도해지이율로 단리 재계산한다")
    void calculateEarlyTermination() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate termination = LocalDate.of(2026, 3, 1);

        when(balanceUseCase.calculate(101L, start, termination)).thenReturn(59_000_000L);

        InterestResult result = service.calculate(new InterestCalculationCommand(
                101L, start, termination, new BigDecimal("1.00"), CalculationMode.SIMPLE));

        assertThat(result.gross()).isEqualTo(1_616L);
        assertThat(result.appliedRate()).isEqualByComparingTo("1.00");

        verify(balanceUseCase).calculate(101L, start, termination);
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 적수를 조회하지 않고 0원을 반환한다")
    void calculateZeroDays() {
        LocalDate date = LocalDate.of(2026, 1, 1);

        InterestResult result = service.calculate(new InterestCalculationCommand(
                101L, date, date, new BigDecimal("3.70"), CalculationMode.MONTHLY_COMPOUND));

        assertThat(result.gross()).isZero();
        assertThat(result.tax()).isZero();
        assertThat(result.net()).isZero();

        verifyNoInteractions(balanceUseCase);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 계산을 거부한다")
    void calculateInvalidDateRange() {
        InterestCalculationCommand command = new InterestCalculationCommand(
                101L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 1),
                new BigDecimal("3.70"),
                CalculationMode.SIMPLE);

        assertThatThrownBy(() -> service.calculate(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));

        verifyNoInteractions(balanceUseCase);
    }

    @Test
    @DisplayName("월복리: 마지막 불완전 월도 실제 일수로 계산한다")
    void calculateMonthlyCompound_partialLastMonth() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        LocalDate mar15 = LocalDate.of(2026, 3, 15);

        when(balanceUseCase.calculate(101L, jan31, feb28)).thenReturn(28_000_000L);

        when(balanceUseCase.calculate(101L, feb28, mar15)).thenReturn(15_000_000L);

        InterestResult result = service.calculate(new InterestCalculationCommand(
                101L, jan31, mar15, new BigDecimal("3.65"), CalculationMode.MONTHLY_COMPOUND));

        // 첫 번째 구간: 28일 → 이자 2,800원
        // 두 번째 구간: 15일
        // (15,000,000 + 2,800 × 15) × 3.65 / 36,500
        // → 이자 1,504원
        assertThat(result.gross()).isEqualTo(4_304L);
        assertThat(result.tax()).isEqualTo(660L);
        assertThat(result.net()).isEqualTo(3_644L);

        verify(balanceUseCase).calculate(101L, jan31, feb28);
        verify(balanceUseCase).calculate(101L, feb28, mar15);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-01-29, 2026-02-28, 2026-03-29",
        "2026-01-30, 2026-02-28, 2026-03-30",
        "2028-01-29, 2028-02-29, 2028-03-29",
        "2028-01-30, 2028-02-29, 2028-03-30",
        "2028-01-31, 2028-02-29, 2028-03-31"
    })
    @DisplayName("월복리: 월말 가입 및 윤년 2월의 구간 경계를 정확히 생성한다")
    void calculateMonthlyCompound_monthEndAndLeapYear(String openedText, String boundaryText, String endText) {

        LocalDate opened = LocalDate.parse(openedText);
        LocalDate boundary = LocalDate.parse(boundaryText);
        LocalDate end = LocalDate.parse(endText);

        InterestResult result = service.calculate(new InterestCalculationCommand(
                101L, opened, end, new BigDecimal("3.70"), CalculationMode.MONTHLY_COMPOUND));

        assertThat(result.gross()).isZero();

        verify(balanceUseCase).calculate(101L, opened, boundary);
        verify(balanceUseCase).calculate(101L, boundary, end);
        verifyNoMoreInteractions(balanceUseCase);
    }
}
