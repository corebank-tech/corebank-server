package com.shinhan.corebank.transfer.application.port.out;

/**
 * LockedAccount가 갖는 계좌 상태. account.domain.AccountStatus와 값 집합은 같지만,
 * LockedAccount의 설계 원칙(account 도메인을 참조하지 않음)을 지키기 위해 별도로 선언한다.
 */
public enum LockedAccountStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
