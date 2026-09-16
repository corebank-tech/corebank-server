package com.shinhan.corebank.signup.application.port.out;

// 원장 계좌가 이미 로컬 account 테이블에 등록돼 있는지 조회한다.
public interface SignupRegisteredAccountPort {

    boolean isRegistered(String accountNumber);
}
