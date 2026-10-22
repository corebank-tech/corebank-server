package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class InterestPaymentPersistenceAdapter implements InterestPaymentHistoryPort {

    private final InterestPaymentJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existsByAccountId(Long accountId) {
        return repository.existsByAccountId(accountId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(InterestPaymentRecord payment) {
        try {
            repository.saveAndFlush(InterestPaymentJpaEntity.from(payment));
        } catch (DataIntegrityViolationException e) {
            // 계좌별 이자 지급 UNIQUE 충돌만 업무 오류로 변환한다.
            String message = e.getMostSpecificCause().getMessage();

            if (message != null && message.contains("uk_interest_payment_account")) {
                throw new BusinessException(SubscriptionErrorCode.INTEREST_ALREADY_PAID, e);
            }

            throw e;
        }
    }
}
