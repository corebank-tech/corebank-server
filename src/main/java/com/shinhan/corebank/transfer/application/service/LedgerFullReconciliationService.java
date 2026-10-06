package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class LedgerFullReconciliationService implements LedgerFullReconciliationUseCase {

    private final AccountBalanceSnapshotPort accountBalanceSnapshotPort;
    private final LedgerReconciliationPort ledgerReconciliationPort;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final int chunkSize;

    public LedgerFullReconciliationService(
            AccountBalanceSnapshotPort accountBalanceSnapshotPort,
            LedgerReconciliationPort ledgerReconciliationPort,
            PlatformTransactionManager transactionManager,
            @Value("${app.ledger-reconciliation.full-scan-chunk-size:1000}") int chunkSize) {
        this.accountBalanceSnapshotPort = accountBalanceSnapshotPort;
        this.ledgerReconciliationPort = ledgerReconciliationPort;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.chunkSize = chunkSize;
    }

    /**
     * 계좌를 account_id 순 청크로 끝까지 훑는다.
     *
     * 청크마다 트랜잭션을 따로 연다. 한 청크의 잔액과 원장 합계는 같은 스냅샷에서 읽어야 그 사이 커밋된
     * 이체 때문에 정상 계좌를 오탐하지 않는다. 반대로 전 계좌를 한 트랜잭션으로 읽으면 스냅샷을 오래
     * 쥐어 undo가 쌓이고 운영 이체가 느려진다.
     */
    @Override
    public List<LedgerReconciliationMismatch> reconcileAll() {
        long startedAt = System.nanoTime();
        List<LedgerReconciliationMismatch> mismatches = new ArrayList<>();
        long scanned = 0;
        long cursor = 0L;
        while (true) {
            long after = cursor;
            SortedMap<Long, Long> balances =
                    readOnlyTransactionTemplate.execute(status -> reconcileChunk(after, mismatches));
            if (balances == null || balances.isEmpty()) {
                break;
            }
            scanned += balances.size();
            cursor = balances.lastKey();
        }
        log.info(
                "원장-잔액 전수 대사 종료 - accounts={}, mismatches={}, elapsedMs={}",
                scanned,
                mismatches.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return mismatches;
    }

    private SortedMap<Long, Long> reconcileChunk(long afterAccountId, List<LedgerReconciliationMismatch> mismatches) {
        SortedMap<Long, Long> balances = accountBalanceSnapshotPort.findBalancesAfter(afterAccountId, chunkSize);
        if (balances.isEmpty()) {
            return balances;
        }
        Map<Long, Long> ledgerBalances =
                ledgerReconciliationPort.sumSignedAmountByAccountIds(new ArrayList<>(balances.keySet()));
        balances.forEach((accountId, accountBalance) -> {
            // 원장이 한 줄도 없는 계좌는 0원이어야 한다 — 잔액이 있으면 원장을 거치지 않은 돈이다
            long ledgerBalance = ledgerBalances.getOrDefault(accountId, 0L);
            if (ledgerBalance != accountBalance) {
                mismatches.add(new LedgerReconciliationMismatch(accountId, ledgerBalance, accountBalance));
                log.error(
                        "[LEDGER_RECONCILIATION_MISMATCH] scope=FULL, accountId={}, ledgerBalance={}, accountBalance={}",
                        accountId,
                        ledgerBalance,
                        accountBalance);
            }
        });
        return balances;
    }
}
