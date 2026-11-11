package com.shinhan.corebank.paymentstop.application.port.out;

import java.util.Map;

// 고객 명의 계좌. account.api 로 읽는다. 고객 이의제기에서 본인 계좌 사건인지 확인한다.
public interface CustomerAccountPort {

    /** accountId → 계좌번호 */
    Map<Long, String> accountNumbersOf(long customerId);
}
