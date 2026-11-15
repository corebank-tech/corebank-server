package com.shinhan.corebank.account.api;

import java.util.Map;

/**
 * 고객이 가진 계좌의 ID 와 계좌번호. 다른 도메인이 계좌 저장소를 보지 않고 본인 계좌인지 확인하려고 쓴다(paymentstop 고객 이의제기, D-36).
 * 상태와 종류를 가리지 않고 그 고객 명의 계좌를 모두 준다.
 */
public interface CustomerAccountNumberQuery {

    /** accountId → 계좌번호. 계좌가 없으면 빈 맵이다. */
    Map<Long, String> findAccountNumbersByCustomerId(long customerId);
}
