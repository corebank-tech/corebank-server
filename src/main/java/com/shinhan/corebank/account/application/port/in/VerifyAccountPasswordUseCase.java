package com.shinhan.corebank.account.application.port.in;

// 계좌비밀번호를 검증하고 성공 시 인증 토큰을 발급한다.
public interface VerifyAccountPasswordUseCase {

    VerifyAccountPasswordResult verify(VerifyAccountPasswordCommand command);
}
