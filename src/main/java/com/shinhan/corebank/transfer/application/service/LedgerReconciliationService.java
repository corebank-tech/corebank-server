package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerReconciliationService implements LedgerReconciliationUseCase {

    private final LedgerReconciliationPort ledgerReconciliationPort;
    private final AccountBalanceSnapshotPort accountBalanceSnapshotPort;

    /**
     * 원장 합계 조회와 잔액 조회를 한 트랜잭션으로 묶는다. 분리돼 있으면 두 조회 사이에 다른
     * 이체가 커밋될 때 서로 다른 시점의 스냅샷을 비교하게 되어 실제로는 정상인 계좌를
     * 오탐(false positive)으로 잡아낼 수 있다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<LedgerReconciliationMismatch> reconcile(LocalDate date) {
        List<Long> accountIds = ledgerReconciliationPort.findAccountIdsPostedBetween(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (accountIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> ledgerBalances = ledgerReconciliationPort.sumSignedAmountByAccountIds(accountIds);
        Map<Long, Long> accountBalances = accountBalanceSnapshotPort.findBalancesByAccountIds(accountIds);

        List<LedgerReconciliationMismatch> mismatches = new ArrayList<>();
        for (Long accountId : accountIds) {
            long ledgerBalance = ledgerBalances.getOrDefault(accountId, 0L);
            // 계좌 자체가 유실된 경우를 "잔액 0원"과 구분한다. 둘 다 0으로 취급하면 원장
            // 합계도 마침 0원인 유실 계좌를 조용히 놓친다 (code-review 지적, PR #463).
            boolean accountMissing = !accountBalances.containsKey(accountId);
            long accountBalance = accountBalances.getOrDefault(accountId, 0L);
            if (accountMissing || ledgerBalance != accountBalance) {
                mismatches.add(new LedgerReconciliationMismatch(accountId, ledgerBalance, accountBalance));
                log.error(
                        "[LEDGER_RECONCILIATION_MISMATCH] date={}, accountId={}, ledgerBalance={}, accountBalance={}",
                        date,
                        accountId,
                        ledgerBalance,
                        accountBalance);
            }
        }
        return mismatches;
    }
}
