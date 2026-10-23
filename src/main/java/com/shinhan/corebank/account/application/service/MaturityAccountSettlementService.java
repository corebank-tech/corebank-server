package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaturityAccountSettlementService implements MaturityAccountSettlementUseCase {

    private final AccountPersistencePort accountPersistencePort;

    private final AccountStatusHistoryPort accountStatusHistoryPort;

    @Override
    @Transactional(readOnly = true)
    public long requireCurrentBalance(Long accountId, Long customerId) {

        // 고객 소유권 확인. AccountJpaEntity는 로드하지 않는다.
        accountPersistencePort
                .findMaturityAccountSnapshot(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        // 잔액만 별도 조회한다.
        Long balance = accountPersistencePort
                .findBalancesByAccountIds(List.of(accountId))
                .get(accountId);

        if (balance == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        return balance;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeMatured(Long accountId, Long customerId, LocalDateTime closedAt, LocalDate closedTradeDate) {

        Account account = accountPersistencePort
                .findByAccountIdAndCustomerIdForUpdate(accountId, customerId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        // 도메인에서 만기 상태 및 잔액 0원 조건 검증
        account.closeMatured(closedAt, closedTradeDate);

        // 계좌 종료 저장
        accountPersistencePort.save(account);

        // 동일 트랜잭션에 감사 이력 저장
        accountStatusHistoryPort.recordMaturityClosure(accountId, account.getMaturityDate(), closedTradeDate, closedAt);
    }
}
