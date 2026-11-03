package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.UnverifiedAccountRange;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class LedgerFullReconciliationServiceTest {

    private static final int CHUNK_SIZE = 2;
    private static final int CHUNK_TIMEOUT_SECONDS = 7;

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
                accountBalanceSnapshotPort,
                ledgerReconciliationPort,
                transactionManager,
                CHUNK_SIZE,
                CHUNK_TIMEOUT_SECONDS);
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

    @Test
    @DisplayName("청크 하나가 실패해도 나머지 청크를 끝까지 확인하고, 실패한 청크의 계좌 구간을 확인 못 한 구간으로 돌려준다(#692)")
    void reconcileAllIsolatingFailures_chunkFails_continuesAndReportsUnverifiedRange() {
        // given — 1·2번 정상, 3·4번 청크의 원장 합계 조회가 실패, 5번은 원장 없이 잔액만 있다
        when(accountBalanceSnapshotPort.findBalancesAfter(0L, CHUNK_SIZE))
                .thenReturn(new TreeMap<>(Map.of(1L, 100L, 2L, 200L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(2L, CHUNK_SIZE))
                .thenAnswer(invocation -> new TreeMap<>(Map.of(3L, 300L, 4L, 400L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(4L, CHUNK_SIZE)).thenReturn(new TreeMap<>(Map.of(5L, 500L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(5L, CHUNK_SIZE)).thenReturn(new TreeMap<>());
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 100L, 2L, 150L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(3L, 4L)))
                .thenThrow(new IllegalStateException("timeout"));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(5L))).thenReturn(Map.of());

        // when
        LedgerFullReconciliationResult result = service.reconcileAllIsolatingFailures();

        // then — 실패한 청크 앞뒤의 불일치는 모두 남고, 실패 구간은 3~4다
        assertThat(result.mismatches())
                .containsExactly(
                        new LedgerReconciliationMismatch(2L, 150L, 200L),
                        new LedgerReconciliationMismatch(5L, 0L, 500L));
        assertThat(result.unverifiedRanges()).containsExactly(new UnverifiedAccountRange(3L, 4L));
    }

    @Test
    @DisplayName("실패한 청크의 계좌 구간을 다시 읽는 것마저 실패하면 거기부터 끝까지를 확인 못 한 구간으로 돌려주고 멈춘다")
    void reconcileAllIsolatingFailures_rangeLookupFails_reportsOpenEndedRange() {
        // given — 첫 청크는 정상, 둘째 청크의 첫 조회는 성공하지만 합계 조회가 실패하고, 구간 재조회도 실패한다
        when(accountBalanceSnapshotPort.findBalancesAfter(0L, CHUNK_SIZE))
                .thenReturn(new TreeMap<>(Map.of(1L, 100L, 2L, 200L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(2L, CHUNK_SIZE))
                .thenReturn(new TreeMap<>(Map.of(3L, 300L, 4L, 400L)))
                .thenThrow(new IllegalStateException("db down"));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 100L, 2L, 200L));
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(3L, 4L)))
                .thenThrow(new IllegalStateException("timeout"));

        // when
        LedgerFullReconciliationResult result = service.reconcileAllIsolatingFailures();

        // then
        assertThat(result.mismatches()).isEmpty();
        assertThat(result.unverifiedRanges()).containsExactly(new UnverifiedAccountRange(3L, null));
        verify(accountBalanceSnapshotPort, never()).findBalancesAfter(4L, CHUNK_SIZE);
    }

    @Test
    @DisplayName("reconcileAll()은 확인 못 한 구간이 있으면 예외로 알려, 구간을 따로 처리하지 않는 호출부가 불완전한 결과를 정상으로 읽지 않게 한다")
    void reconcileAll_unverifiedRanges_throws() {
        when(accountBalanceSnapshotPort.findBalancesAfter(0L, CHUNK_SIZE))
                .thenReturn(new TreeMap<>(Map.of(1L, 100L, 2L, 200L)))
                .thenReturn(new TreeMap<>(Map.of(1L, 100L, 2L, 200L)));
        when(accountBalanceSnapshotPort.findBalancesAfter(2L, CHUNK_SIZE)).thenReturn(new TreeMap<>());
        when(ledgerReconciliationPort.sumSignedAmountByAccountIds(List.of(1L, 2L)))
                .thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.reconcileAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1~2");
    }

    @Test
    @DisplayName("청크 트랜잭션에 설정한 제한 시간이 걸려 있어, 느린 쿼리가 예외 없이 대사를 멈추지 못한다(#692)")
    void chunkTransactionTemplate_hasConfiguredTimeout() {
        TransactionTemplate template =
                (TransactionTemplate) ReflectionTestUtils.getField(service, "readOnlyTransactionTemplate");

        assertThat(template.getTimeout()).isEqualTo(CHUNK_TIMEOUT_SECONDS);
        assertThat(template.isReadOnly()).isTrue();
    }
}
