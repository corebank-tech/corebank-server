package com.shinhan.corebank.subscription.adapter.out.auth;

import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionPasswordVerificationPort;
import org.springframework.stereotype.Component;

/**
 * 계좌 비밀번호 인증 기능 연동 전까지 사용하는 임시 Adapter.
 * 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다
 * (PR #140 사례) - P6 실구현 전까지 모든 프로필에서 활성화한다.
 */
@Component
public class ProductSubscriptionAuthTokenMockAdapter implements ProductSubscriptionPasswordVerificationPort {
    @Override
    public void verifyAccountPasswordToken(String token, Long customerId, Long accountId) {
        // 항상 통과 -> P6 실제 구현 전까지 임시 대체
    }
}
