package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.RecentTransferAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.RecentTransferAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.RecentDepositAccount;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferAccountQueryPort;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecentTransferAccountQueryService implements RecentTransferAccountQueryUseCase {

    private static final int LOOKBACK_MONTHS = 6;
    private static final int LIMIT = 5;

    private final RecentTransferAccountQueryPort queryPort;
    private final AccountLockPort accountLockPort;
    private final Clock clock;

    public RecentTransferAccountQueryService(
            RecentTransferAccountQueryPort queryPort, AccountLockPort accountLockPort, Clock clock) {
        this.queryPort = queryPort;
        this.accountLockPort = accountLockPort;
        this.clock = clock;
    }

    @Override
    public List<RecentTransferAccountResult> queryRecent(Long customerId) {
        LocalDateTime since = LocalDateTime.now(clock).minusMonths(LOOKBACK_MONTHS);
        List<RecentDepositAccount> recentAccounts = queryPort.findRecentDepositAccounts(customerId, since, LIMIT);
        if (recentAccounts.isEmpty()) {
            return List.of();
        }
        Map<String, ResolvedPayee> payeesByAccountNumber =
                accountLockPort.resolvePayeesByAccountNumbers(recentAccounts.stream()
                        .map(RecentDepositAccount::depositAccountNumber)
                        .toList());
        return recentAccounts.stream()
                .map(recent -> toResult(recent, payeesByAccountNumber.get(recent.depositAccountNumber())))
                .filter(Objects::nonNull)
                .toList();
    }

    // 예금주명은 지금 계좌에서 읽으므로 계좌를 찾지 못하면 보여 줄 이름이 없어 뺀다
    private static RecentTransferAccountResult toResult(RecentDepositAccount recent, ResolvedPayee payee) {
        if (payee == null) {
            return null;
        }
        return new RecentTransferAccountResult(
                recent.depositAccountNumber(),
                payee.payeeName(),
                recent.lastTransferredAt(),
                payee.status() == LockedAccountStatus.ACTIVE);
    }
}
