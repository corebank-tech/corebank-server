package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryResult;
import com.shinhan.corebank.subscription.domain.LedgerBalancePoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccumulatedDailyBalanceServiceTest {

    @Mock
    private LedgerBalanceHistoryPort ledgerBalanceHistoryPort;

    @InjectMocks
    private AccumulatedDailyBalanceService accumulatedDailyBalanceService;

    @Test
    @DisplayName("조회한 시작 잔액과 원장 이력을 사용해 적수를 계산한다")
    void calculate() {
        Long accountId = 101L;
        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 4);

        List<LedgerBalancePoint> points = List.of(
                new LedgerBalancePoint(LocalDate.of(2026, 9, 2), LocalDateTime.of(2026, 9, 2, 10, 0), 1L, 1_500L));

        when(ledgerBalanceHistoryPort.load(accountId, fromInclusive, toExclusive))
                .thenReturn(new LedgerBalanceHistoryResult(1_000L, points));

        long result = accumulatedDailyBalanceService.calculate(accountId, fromInclusive, toExclusive);

        // 9/1 = 1,000
        // 9/2 = 1,500
        // 9/3 = 1,500
        assertThat(result).isEqualTo(4_000L);

        verify(ledgerBalanceHistoryPort).load(accountId, fromInclusive, toExclusive);
    }

    @Test
    @DisplayName("기간 중 원장 거래가 없으면 시작 잔액으로 적수를 계산한다")
    void calculate_withoutLedgerEntries() {
        Long accountId = 101L;
        LocalDate fromInclusive = LocalDate.of(2026, 9, 1);
        LocalDate toExclusive = LocalDate.of(2026, 9, 4);

        when(ledgerBalanceHistoryPort.load(accountId, fromInclusive, toExclusive))
                .thenReturn(new LedgerBalanceHistoryResult(2_000L, List.of()));

        long result = accumulatedDailyBalanceService.calculate(accountId, fromInclusive, toExclusive);

        assertThat(result).isEqualTo(6_000L);
    }

    @Test
    @DisplayName("계좌 ID가 없으면 REQUIRED_FIELD_MISSING 예외가 발생한다")
    void calculate_missingAccountId() {
        assertThatThrownBy(() -> accumulatedDailyBalanceService.calculate(
                        null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;

                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
                });
    }
}
