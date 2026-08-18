package com.shinhan.corebank.transfer.application.port.out;

/**
 * ResolvedPayee가 갖는 입금계좌 상품 유형. account.domain.AccountType과 값 집합은 같지만,
 * {@link LockedAccountStatus}와 동일한 이유(account 도메인을 참조하지 않음)로 별도 선언한다.
 */
public enum LockedAccountType {
    DEMAND_DEPOSIT,
    TIME_DEPOSIT,
    INSTALLMENT_SAVINGS
}
