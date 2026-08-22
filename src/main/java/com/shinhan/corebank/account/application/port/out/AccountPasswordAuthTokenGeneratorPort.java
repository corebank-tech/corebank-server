package com.shinhan.corebank.account.application.port.out;

// 예측 불가능한 계좌비밀번호 인증 토큰을 생성한다.
public interface AccountPasswordAuthTokenGeneratorPort {

    String generate();
}
