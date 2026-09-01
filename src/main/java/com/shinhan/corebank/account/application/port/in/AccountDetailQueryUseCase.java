package com.shinhan.corebank.account.application.port.in;

// 로그인 고객이 소유한 계좌의 상세정보를 조회한다.
public interface AccountDetailQueryUseCase {

    AccountDetailResult getDetail(Long customerId, Long accountId);
}
