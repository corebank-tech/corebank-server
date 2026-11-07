package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.application.port.out.AccumulatedDailyBalanceSqlPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryResult;
import com.shinhan.corebank.subscription.domain.LedgerBalancePoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccumulatedDailyBalanceServiceTest {

    @Mock
    private LedgerBalanceHistoryPort ledgerBalanceHistoryPort;

    @Mock
    private AccumulatedDailyBalanceSqlPort sqlPort;

    private AccumulatedDailyBalanceService service;

    @BeforeEach
    void setUp() {
        service = new AccumulatedDailyBalanceService(ledgerBalanceHistoryPort, sqlPort, false);
    }

    @Test
    @DisplayName("기본 설정에서는 기존 적수 계산을 사용한다")
    void calculate_legacy() {

        Long accountId = 101L;
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 4);

        List<LedgerBalancePoint> points = List.of(
                new LedgerBalancePoint(LocalDate.of(2026, 9, 2), LocalDateTime.of(2026, 9, 2, 10, 0), 1L, 1_500L));

        when(ledgerBalanceHistoryPort.load(accountId, from, to))
                .thenReturn(new LedgerBalanceHistoryResult(1_000L, points));

        long result = service.calculate(accountId, from, to);

        assertThat(result).isEqualTo(4_000L);

        verify(ledgerBalanceHistoryPort).load(accountId, from, to);

        verifyNoInteractions(sqlPort);
    }

    @Test
    @DisplayName("SQL 활성화 시 SQL 집계 결과를 반환한다")
    void calculate_sqlEnabled() {

        service = new AccumulatedDailyBalanceService(ledgerBalanceHistoryPort, sqlPort, true);

        Long accountId = 101L;
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 4);

        when(sqlPort.calculate(List.of(accountId), from, to)).thenReturn(Map.of(accountId, 4_000L));

        long result = service.calculate(accountId, from, to);

        assertThat(result).isEqualTo(4_000L);

        verify(sqlPort).calculate(List.of(accountId), from, to);
        verifyNoInteractions(ledgerBalanceHistoryPort);
    }

    @Test
    @DisplayName("SQL 결과에 대상 계좌가 없으면 실패한다")
    void calculate_sqlMissingAccount() {

        service = new AccumulatedDailyBalanceService(ledgerBalanceHistoryPort, sqlPort, true);

        Long accountId = 101L;
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 4);

        when(sqlPort.calculate(List.of(accountId), from, to)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.calculate(accountId, from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accountId=101");
    }

    @Test
    @DisplayName("계좌 ID가 없으면 REQUIRED_FIELD_MISSING")
    void calculate_missingAccountId() {

        assertThatThrownBy(() -> service.calculate(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;

                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
                });
    }
}
