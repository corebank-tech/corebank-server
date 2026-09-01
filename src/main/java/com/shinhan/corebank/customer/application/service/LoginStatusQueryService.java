package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginStatusQueryService implements LoginStatusQueryUseCase {
    private final CustomerPersistencePort customerPersistencePort;
    private final AccountOverviewQueryUseCase accountOverviewQueryUseCase;

    @Override
    public LoginStatusResult getLoginStatus(Long customerId) {
        Customer customer = customerPersistencePort
                .findById(customerId)
                .orElseThrow(() -> new IllegalStateException("로그인 상태 조회 대상 고객이 존재하지 않습니다."));
        LocalDateTime lastTransactionAt = findLastTransactionAt(customerId);
        return new LoginStatusResult(customer.getPreviousLoginAt(), customer.getLastLoginIp(), lastTransactionAt);
    }

    private LocalDateTime findLastTransactionAt(Long customerId) {
        AccountOverviewResult overview = accountOverviewQueryUseCase.getOverview(customerId);
        return overview.items().stream()
                .flatMap(g -> g.accounts().stream())
                .map(AccountOverviewResult.AccountItem::lastTransactionAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
