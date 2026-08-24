package com.shinhan.corebank.signup.adapter.out.account;

import com.shinhan.corebank.account.api.RegisteredAccountQuery;
import com.shinhan.corebank.signup.application.port.out.SignupRegisteredAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// signup의 계좌 등록조회 포트를 account 공개 API에 연결한다.
@Component
@RequiredArgsConstructor
public class SignupRegisteredAccountAdapter
        implements SignupRegisteredAccountPort {

    private final RegisteredAccountQuery registeredAccountQuery;

    @Override
    public boolean isRegistered(String accountNumber) {
        return registeredAccountQuery.existsByAccountNumber(accountNumber);
    }
}
