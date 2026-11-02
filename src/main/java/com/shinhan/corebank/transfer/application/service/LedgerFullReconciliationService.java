package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.UnverifiedAccountRange;
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
    public LedgerFullReconciliationResult reconcileAllIsolatingFailures() {
        long startedAt = System.nanoTime();
        List<LedgerReconciliationMismatch> mismatches = new ArrayList<>();
        List<UnverifiedAccountRange> unverifiedRanges = new ArrayList<>();
        long scanned = 0;
        long cursor = 0L;
        while (true) {
            long after = cursor;
            try {
                ChunkScan scan = readOnlyTransactionTemplate.execute(status -> reconcileChunk(after));
                if (scan == null || scan.balances().isEmpty()) {
                    break;
                }
                mismatches.addAll(scan.mismatches());
                scanned += scan.balances().size();
                cursor = scan.balances().lastKey();
            } catch (RuntimeException e) {
                // 한 청크의 실패가 전체를 멈추지 않게 한다. 확인 못 한 구간은 호출부가 마감을 멈추는 근거가 된다
                log.error("원장-잔액 전수 대사 청크 실패 - afterAccountId={}", after, e);
                SortedMap<Long, Long> failedChunkKeys = findChunkKeysQuietly(after);
                if (failedChunkKeys == null) {
                    unverifiedRanges.add(new UnverifiedAccountRange(after + 1, null));
                    break;
                }
                if (failedChunkKeys.isEmpty()) {
                    break;
                }
                unverifiedRanges.add(new UnverifiedAccountRange(failedChunkKeys.firstKey(), failedChunkKeys.lastKey()));
                cursor = failedChunkKeys.lastKey();
            }
        }
        log.info(
                "원장-잔액 전수 대사 종료 - accounts={}, mismatches={}, unverifiedRanges={}, elapsedMs={}",
                scanned,
                mismatches.size(),
                unverifiedRanges.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new LedgerFullReconciliationResult(mismatches, unverifiedRanges);
    }

    // 실패한 청크의 계좌 범위를 알아내려고 키만 다시 읽는다. 이것마저 실패하면 null이라 더 나아갈 수 없다
    private SortedMap<Long, Long> findChunkKeysQuietly(long afterAccountId) {
        try {
            return readOnlyTransactionTemplate.execute(
                    status -> accountBalanceSnapshotPort.findBalancesAfter(afterAccountId, chunkSize));
        } catch (RuntimeException e) {
            log.error("원장-잔액 전수 대사 실패 청크의 계좌 범위 재조회 실패 - afterAccountId={}", afterAccountId, e);
            return null;
        }
    }

    // 청크가 끝까지 성공한 뒤에만 불일치를 호출부로 넘기려고 청크 안에서 따로 모은다
    private ChunkScan reconcileChunk(long afterAccountId) {
        SortedMap<Long, Long> balances = accountBalanceSnapshotPort.findBalancesAfter(afterAccountId, chunkSize);
        List<LedgerReconciliationMismatch> chunkMismatches = new ArrayList<>();
        if (balances.isEmpty()) {
            return new ChunkScan(balances, chunkMismatches);
        }
        Map<Long, Long> ledgerBalances =
                ledgerReconciliationPort.sumSignedAmountByAccountIds(new ArrayList<>(balances.keySet()));
        balances.forEach((accountId, accountBalance) -> {
            // 원장이 한 줄도 없는 계좌는 0원이어야 한다 — 잔액이 있으면 원장을 거치지 않은 돈이다
            long ledgerBalance = ledgerBalances.getOrDefault(accountId, 0L);
            if (ledgerBalance != accountBalance) {
                chunkMismatches.add(new LedgerReconciliationMismatch(accountId, ledgerBalance, accountBalance));
                log.error(
                        "[LEDGER_RECONCILIATION_MISMATCH] scope=FULL, accountId={}, ledgerBalance={}, accountBalance={}",
                        accountId,
                        ledgerBalance,
                        accountBalance);
            }
        });
        return new ChunkScan(balances, chunkMismatches);
    }

    private record ChunkScan(SortedMap<Long, Long> balances, List<LedgerReconciliationMismatch> mismatches) {}
}
