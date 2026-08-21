package com.shinhan.corebank.account.api;

// 기존 은행 원장의 계좌를 로컬 account 테이블에 등록하는 기능을 공개한다.
public interface ExistingAccountRegistration {

    void registerAll(RegisterExistingAccountsCommand command);
}
