package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.AccountBalanceQuery;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 다른 도메인이 계좌 잔액만 물어볼 수 있게 조회 기능을 제공한다.
@Service
@RequiredArgsConstructor
public class AccountBalanceQueryService implements AccountBalanceQuery {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds) {
        return accountPersistencePort.findBalancesByAccountIds(accountIds);
    }
}
