package com.shinhan.corebank.autotransfer.adapter.out.auth;

import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test","scratch"})
public class MockAuthTokenVerificationPort implements AuthTokenVerificationPort {
    @Override
    public void verify(String authToken, Long accountId, String purpose) {
        // 항상 통과 -> P6 실제 구현 전까지 임시 대체
    }
}
