package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.AccountMatured;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMaturityTransitionService {

    private final AccountPersistencePort accountPersistencePort;
    private final AccountStatusHistoryPort accountStatusHistoryPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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

                    LocalDateTime changedAt = LocalDateTime.now(clock);

                    // 계좌별 상태 전이 감사 이력 저장
                    accountStatusHistoryPort.recordMaturity(
                            account.getAccountId(), account.getMaturityDate(), maturityBaseDate, changedAt);

                    // 실제 상태가 변경된 경우에만 이벤트 발행
                    eventPublisher.publishEvent(AccountMatured.builder()
                            .customerId(account.getCustomerId())
                            .refId(account.getAccountId())
                            .status(ProcessResultStatus.SUCCESS)
                            .errorCode(null)
                            .occurredAt(changedAt)
                            .accountId(account.getAccountId())
                            .maturityDate(account.getMaturityDate())
                            .maturityBaseDate(maturityBaseDate)
                            .build());

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
