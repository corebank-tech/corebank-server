package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.CustomerAccountNumberQuery;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 다른 도메인이 고객 명의 계좌 ID 와 계좌번호만 물어볼 수 있게 한다.
@Service
@RequiredArgsConstructor
public class CustomerAccountNumberQueryService implements CustomerAccountNumberQuery {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findAccountNumbersByCustomerId(long customerId) {
        Map<Long, String> numbers = new LinkedHashMap<>();
        for (Account account : accountPersistencePort.findAllByCustomerId(customerId)) {
            numbers.put(account.getAccountId(), account.getAccountNumber());
        }
        return numbers;
    }
}
