package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginStatusQueryService implements LoginStatusQueryUseCase {
    private final LoginHistoryQueryPort loginHistoryQueryPort;
    private final AccountOverviewQueryUseCase accountOverviewQueryUseCase;

    @Override
    public LoginStatusResult getLoginStatus(Long customerId, String currentIp) {
        LocalDateTime previousLoginAt = loginHistoryQueryPort.findPreviousSuccessfulLogin(customerId).orElse(null);
        LocalDateTime lastTransactionAt = findLastTransactionAt(customerId);
        return new LoginStatusResult(previousLoginAt, currentIp, lastTransactionAt);
    }

    private LocalDateTime findLastTransactionAt(Long customerId) {
        AccountOverviewResult overview = accountOverviewQueryUseCase.getOverview(customerId);
        return overview.items().stream().flatMap(group -> group.accounts().stream())
                .map(AccountOverviewResult.AccountItem::lastTransactionAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
