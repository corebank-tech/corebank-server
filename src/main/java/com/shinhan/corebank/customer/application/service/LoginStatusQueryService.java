package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.AccountLastTransactionQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginStatusQueryService implements LoginStatusQueryUseCase {
    private final CustomerPersistencePort customerPersistencePort;
    private final AccountLastTransactionQueryPort accountLastTransactionQueryPort;

    @Override
    public LoginStatusResult getLoginStatus(Long customerId) {
        Customer customer = customerPersistencePort.findById(customerId).orElseThrow(()-> new IllegalStateException("로그인 상태 조회 대상 고객이 존재하지 않습니다."));
        LocalDateTime lastTransactionAt = accountLastTransactionQueryPort.findLatestTransactionAt(customerId).orElse(null);
        return new LoginStatusResult(customer.getPreviousLoginAt(), customer.getLastLoginIp(), lastTransactionAt);
    }
}
