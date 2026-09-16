package com.shinhan.corebank.account.api;

// 계좌 저장소를 외부에 노출하지 않고 계좌번호 등록 여부만 공개한다.
public interface RegisteredAccountQuery {

    boolean existsByAccountNumber(String accountNumber);
}
