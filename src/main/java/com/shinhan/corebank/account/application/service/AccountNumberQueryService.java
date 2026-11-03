package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountNumberQueryUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountNumberQueryService implements AccountNumberQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public Optional<String> findAccountNumber(Long accountId, Long customerId) {
        // 전체 Account 엔티티를 조회하면 같은 트랜잭션에서
        // transfer 도메인이 account.version을 갱신한 후
        // 계좌를 재조회할 때 영속성 컨텍스트 버전 충돌이 발생할 수 있다.
        return accountPersistencePort.findAccountNumberByIdAndCustomerId(accountId, customerId);
    }
}
