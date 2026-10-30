package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationPort;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class LedgerFullReconciliationServiceTest {

    private static final int CHUNK_SIZE = 2;

    @Mock
    AccountBalanceSnapshotPort accountBalanceSnapshotPort;

    @Mock
    LedgerReconciliationPort ledgerReconciliationPort;

    @Mock
    PlatformTransactionManager transactionManager;

    LedgerFullReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new LedgerFullReconciliationService(
                accountBalanceSnapshotPort, ledgerReconciliationPort, transactionManager, CHUNK_SIZE);
    }

    @Test
    @DisplayName("계좌를 청크 단위로 끝까지 넘기며, 원장 합계가 다르거나 원장이 아예 없는데 잔액만 있는 계좌를 탐지한다")
    void reconcileAll_walksAllChunksAndDetectsMismatches() {
        // given — 1·2번이 첫 청크, 3번이 둘째 청크, 그 뒤는 비어 있다
        when(accountBalanceSnapshotPort.findBalancesAfter(0L, CHUNK_SIZE))
                .thenReturn(new TreeMap<>(Map.of(1L, 100L, 2L, 200L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(2L, CHUNK_SIZE)).thenReturn(new TreeMap<>(Map.of(3L, 300L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(3L, CHUNK_SIZE)).thenReturn(new TreeMap<>());
        // 2번은 원장(150)과 잔액(200)이 어긋나고, 3번은 원장 기표가 한 줄도 없다
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 100L, 2L, 150L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(3L))).thenReturn(Map.of());

        // when
        List<LedgerReconciliationMismatch> mismatches = service.reconcileAll();

        // then
        assertThat(mismatches)
                .containsExactly(
                        new LedgerReconciliationMismatch(2L, 150L, 200L),
                        new LedgerReconciliationMismatch(3L, 0L, 300L));
        verify(accountBalanceSnapshotPort).findBalancesAfter(3L, CHUNK_SIZE);
    }

    @Test
    @DisplayName("계좌가 하나도 없으면 원장은 조회하지 않고 빈 결과를 돌려준다")
    void reconcileAll_returnsEmpty_whenNoAccounts() {
        // given
        when(accountBalanceSnapshotPort.findBalancesAfter(0L, CHUNK_SIZE)).thenReturn(new TreeMap<>());

        // when
        List<LedgerReconciliationMismatch> mismatches = service.reconcileAll();

        // then
        assertThat(mismatches).isEmpty();
        verifyNoInteractions(ledgerReconciliationPort);
    }
}
