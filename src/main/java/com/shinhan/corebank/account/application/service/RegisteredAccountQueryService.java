package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.RegisteredAccountQuery;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 다른 도메인이 계좌 등록 여부만 물어볼 수 있게 조회 기능을 제공한다.
@Service
@RequiredArgsConstructor
public class RegisteredAccountQueryService implements RegisteredAccountQuery {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return accountPersistencePort.existsByAccountNumber(accountNumber);
    }
}
