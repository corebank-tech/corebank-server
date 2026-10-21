package com.shinhan.corebank.business.application.service;

import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.business.application.port.out.BusinessDateCommandPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BusinessDateGateService implements BusinessDateGate {

    private final BusinessDateCommandPort businessDateCommandPort;

    // 트랜잭션 밖에서 부르면 락이 읽자마자 풀리므로 예외로 막는다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate acquireSharedAndReadBusinessDate() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof RememberedBusinessDate remembered) {
                return remembered.businessDate();
            }
        }
        LocalDate businessDate = businessDateCommandPort
                .findBusinessDateForShare(BusinessDateType.BUSINESS_DATE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_DATE_NOT_FOUND));
        TransactionSynchronizationManager.registerSynchronization(new RememberedBusinessDate(businessDate));
        return businessDate;
    }

    // 트랜잭션에 붙는 동기화 객체라 REQUIRES_NEW 로 중단·재개될 때 값이 바깥 트랜잭션과 섞이지 않는다
    private record RememberedBusinessDate(LocalDate businessDate) implements TransactionSynchronization {}
}
