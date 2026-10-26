package com.shinhan.corebank.account.api;

import java.time.LocalDate;

/** 다른 도메인에 공개하는 만기해지 계좌 검증 계약. */
public interface MaturityAccountQueryUseCase {

    /**
     * 본인 소유의 만기 도래한 MATURED 상품 계좌를 확인한다.
     *
     * @param actualDate 실제 해지일(KST)
     */
    MaturityAccountView requireMatured(Long accountId, Long customerId, LocalDate actualDate);

    /**
     * 원리금 수취 계좌의 소유권, 유형, 상태,
     * 출금계좌 등록 여부를 사전 확인한다.
     */
    void requireWithdrawalDestination(Long accountId, Long customerId);

    /**
     * 실제 원리금 이체 후 계좌 락을 보유한 상태에서
     * 수취 계좌 자격을 재확인한다.
     *
     * 만기해지 트랜잭션에서 Gate 획득 및 MATURITY 이체 이후 호출한다.
     */
    void requireWithdrawalDestinationForUpdate(Long accountId, Long customerId);
}
