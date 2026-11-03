package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.EarlyTerminationAccountSettlementUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EarlyTerminationAccountSettlementService implements EarlyTerminationAccountSettlementUseCase {

    private final AccountPersistencePort accountPersistencePort;
    private final AccountStatusHistoryPort accountStatusHistoryPort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeEarly(Long accountId, Long customerId, LocalDateTime closedAt, LocalDate closedTradeDate) {
        Account account = accountPersistencePort
                .findByAccountIdAndCustomerIdForUpdate(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        // 반드시 TERMINATION 이체 후 잔액 0원을 잠금 조회한 Account에서 검증한다.
        account.closeEarly(closedAt, closedTradeDate);
        accountPersistencePort.save(account);
        accountStatusHistoryPort.recordEarlyClosure(accountId, account.getMaturityDate(), closedTradeDate, closedAt);
    }
}
