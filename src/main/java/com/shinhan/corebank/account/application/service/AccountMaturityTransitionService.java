package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountMaturityTransitionService {

    private final AccountPersistencePort accountPersistencePort;

    @Transactional
    public int transition(List<Long> accountIds, LocalDate maturityBaseDate) {

        int transitionedCount = 0;

        for (Long accountId : accountIds) {

            // 잠금 조회로 다른 거래와의 동시 상태 변경을 방지한다.
            Account account = accountPersistencePort
                    .findByAccountIdForUpdate(accountId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));

            // 후보 조회 이후 상태가 달라졌을 수 있으므로 다시 확인한다.
            if (account.getStatus() != AccountStatus.ACTIVE) {
                continue;
            }

            if (account.getAccountType() == AccountType.DEMAND_DEPOSIT) {
                continue;
            }

            if (account.getMaturityDate() == null || account.getMaturityDate().isAfter(maturityBaseDate)) {
                continue;
            }

            if (account.markMatured(maturityBaseDate)) {
                accountPersistencePort.save(account);
                transitionedCount++;
            }
        }

        return transitionedCount;
    }
}
