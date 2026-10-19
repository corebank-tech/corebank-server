package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMaturityTransitionService {

    private final AccountPersistencePort accountPersistencePort;

    @Transactional
    public int transition(List<Long> accountIds, LocalDate maturityBaseDate) {
        int transitionedCount = 0;

        for (Long accountId : accountIds) {
            try {
                Account account = accountPersistencePort
                        .findByAccountIdForUpdate(accountId)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));

                if (account.getStatus() != AccountStatus.ACTIVE) {
                    continue;
                }

                if (!account.getAccountType().isMaturityEligible()) {
                    continue;
                }

                if (account.getMaturityDate() == null
                        || account.getMaturityDate().isAfter(maturityBaseDate)) {
                    continue;
                }

                if (account.markMatured(maturityBaseDate)) {
                    accountPersistencePort.save(account);
                    transitionedCount++;
                }

            } catch (RuntimeException ex) {
                log.error("만기 전이 실패 - accountId={}, maturityBaseDate={}", accountId, maturityBaseDate, ex);

                throw new BusinessException(
                        CommonErrorCode.INTERNAL_ERROR,
                        new IllegalStateException("만기 전이 실패 accountId=" + accountId, ex));
            }
        }

        return transitionedCount;
    }
}
