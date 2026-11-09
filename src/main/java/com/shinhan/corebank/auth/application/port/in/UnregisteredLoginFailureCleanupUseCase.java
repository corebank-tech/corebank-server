package com.shinhan.corebank.auth.application.port.in;

// 만료된 미가입 아이디 로그인 실패 기록을 지운다
public interface UnregisteredLoginFailureCleanupUseCase {

    int deleteExpired();
}
