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

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerReconciliationService implements LedgerReconciliationUseCase {

    private final LedgerReconciliationPort ledgerReconciliationPort;
    private final AccountBalanceSnapshotPort accountBalanceSnapshotPort;

    @Override
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
            long accountBalance = accountBalances.getOrDefault(accountId, 0L);
            if (ledgerBalance != accountBalance) {
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
