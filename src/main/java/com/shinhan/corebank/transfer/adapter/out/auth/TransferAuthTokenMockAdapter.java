package com.shinhan.corebank.transfer.adapter.out.auth;

import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import org.springframework.stereotype.Component;

/**
 * autotransfer.MockAuthTokenVerificationPort와 동일하게, P6(OTP/계좌비밀번호 인증)가 아직
 * 착수 전이라 항상 통과시킨다. 클래스명은 이 모듈의 *Adapter 네이밍 컨벤션 및 빈 이름 충돌
 * 회피(둘 다 단순명이 MockAuthTokenVerificationPort였다면 충돌) 이유로 구분했다.
 * 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - P6 실구현 전까지 모든 프로필에서 활성화한다.
 */
@Component
public class TransferAuthTokenMockAdapter implements TransferAuthTokenVerificationPort {
    @Override
    public void verify(String authToken, Long accountId, String purpose) {
        // 항상 통과 -> P6 실제 구현 전까지 임시 대체
    }
}
