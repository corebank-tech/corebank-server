package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.application.port.out.WithdrawalAccountPasswordVerificationPort;
import org.springframework.stereotype.Component;

/**
 * 계좌 비밀번호 인증 기능 연동 전까지 사용하는 임시 Adapter.
 * P6 구현 완료 후 실제 인증 Adapter로 교체한다.
 */
// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - P6 실구현 전까지 모든 프로필에서 활성화한다.
@Component
public class WithdrawalAccountAuthTokenMockAdapter
        implements WithdrawalAccountPasswordVerificationPort {

    @Override
    public void verifyAccountPasswordToken(
            String token,
            Long customerId,
            Long accountId
    ) {
        // TODO P6 AccountPasswordAuthTokenVerifier 연동
    }
}
