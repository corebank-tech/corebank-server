package com.shinhan.corebank.subscription.api;

/**
 * 예적금 이자 지급 공개 계약.
 *
 * <p>호출자 트랜잭션 안에서만 실행한다(MANDATORY). 원장 입금, GL 전표,
 * 지급 이력을 같은 트랜잭션에서 커밋하거나 롤백한다.
 *
 * <p>호출자는 계좌를 잠그기 전에 BusinessDateGate를 먼저 획득해야 한다.
 * pay() 내부의 Gate 호출은 이미 획득한 락의 순서를 바로잡지 못한다.
 *
 * <p>이미 이자가 지급된 계좌는 PRD0302로 거부한다.
 * 이 예외가 발생하면 호출자 트랜잭션은 롤백된다.
 */
public interface InterestPaymentUseCase {

    InterestPaymentResult pay(InterestPaymentCommand command);
}
