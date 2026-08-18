package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import com.shinhan.corebank.customer.application.port.out.PreviousLoginRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginStatusQueryService implements LoginStatusQueryUseCase {
    private final LoginHistoryQueryPort loginHistoryQueryPort;

    @Override
    public LoginStatusResult getLoginStatus(Long customerId) {
        return loginHistoryQueryPort.findPreviousSuccessfulLogin(customerId)
                .map(this::toResult)
                .orElseGet(()-> new LoginStatusResult(null, null));
    }

    private LoginStatusResult toResult(PreviousLoginRecord record) {
        return new LoginStatusResult(record.loginAt(), record.loginIp());
    }
}
