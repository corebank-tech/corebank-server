package com.shinhan.corebank.scheduledtransfer.adapter.out.auth;

import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import org.springframework.stereotype.Component;

// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - 실구현 전까지 모든 프로필에서 활성화한다.
@Component
public class MockAuthTokenVerificationPort implements AuthTokenVerificationPort {
    @Override
    public void verify(String authToken, Long accountId, String purpose) {
        // 항상 통과 -> P6 실제 구현 전까지 임시 대체
    }
}
