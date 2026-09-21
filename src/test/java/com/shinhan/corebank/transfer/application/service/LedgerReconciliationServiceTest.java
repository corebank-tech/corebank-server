package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationServiceTest {

    @Mock
    LedgerReconciliationPort ledgerReconciliationPort;

    @Mock
    AccountBalanceSnapshotPort accountBalanceSnapshotPort;

    @InjectMocks
    LedgerReconciliationService ledgerReconciliationService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 9);
    private static final LocalDateTime FROM = DATE.atStartOfDay();
    private static final LocalDateTime TO = DATE.plusDays(1).atStartOfDay();

    @Test
    @DisplayName("전일 기표가 없으면 빈 결과를 반환하고 잔액 조회는 하지 않는다")
    void reconcile_returnsEmpty_whenNoAccountsPosted() {
        when(ledgerReconciliationPort.findAccountIdsPostedBetween(FROM, TO)).thenReturn(List.of());

        List<LedgerReconciliationMismatch> mismatches = ledgerReconciliationService.reconcile(DATE);

        assertThat(mismatches).isEmpty();
        verifyNoInteractions(accountBalanceSnapshotPort);
    }

    @Test
    @DisplayName("원장 합계와 계좌 잔액이 모두 일치하면 빈 결과를 반환한다")
    void reconcile_returnsEmpty_whenAllBalancesMatch() {
        when(ledgerReconciliationPort.findAccountIdsPostedBetween(FROM, TO)).thenReturn(List.of(101L, 202L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 2000L, 202L, 5000L));
        when(accountBalanceSnapshotPort.findBalancesByAccountIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 2000L, 202L, 5000L));

        List<LedgerReconciliationMismatch> mismatches = ledgerReconciliationService.reconcile(DATE);

        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("원장 합계와 계좌 잔액이 다른 계좌만 불일치로 반환한다")
    void reconcile_returnsOnlyMismatchedAccounts() {
        when(ledgerReconciliationPort.findAccountIdsPostedBetween(FROM, TO)).thenReturn(List.of(101L, 202L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 2000L, 202L, 5000L));
        // 101은 일치, 202는 화면상 잔액이 4000원 모자람
        when(accountBalanceSnapshotPort.findBalancesByAccountIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 2000L, 202L, 1000L));

        List<LedgerReconciliationMismatch> mismatches = ledgerReconciliationService.reconcile(DATE);

        assertThat(mismatches).containsExactly(new LedgerReconciliationMismatch(202L, 5000L, 1000L));
    }

    @Test
    @DisplayName("잔액 조회 결과에 계좌가 아예 없으면(계좌 유실) 화면상 잔액을 0으로 간주해 불일치 처리한다")
    void reconcile_treatsMissingAccountBalanceAsZero() {
        when(ledgerReconciliationPort.findAccountIdsPostedBetween(FROM, TO)).thenReturn(List.of(101L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(101L)))
                .thenReturn(Map.of(101L, 2000L));
        when(accountBalanceSnapshotPort.findBalancesByAccountIds(List.of(101L))).thenReturn(Map.of());

        List<LedgerReconciliationMismatch> mismatches = ledgerReconciliationService.reconcile(DATE);

        assertThat(mismatches).containsExactly(new LedgerReconciliationMismatch(101L, 2000L, 0L));
    }
}
