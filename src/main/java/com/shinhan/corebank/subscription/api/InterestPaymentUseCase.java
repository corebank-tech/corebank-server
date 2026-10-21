package com.shinhan.corebank.subscription.api;

/**
 * 예적금 이자 지급 공개 계약.
 *
 * 호출자의 트랜잭션 안에서 실행되어야 한다.
 * 실제 이자 입금, GL 전표, 지급 이력은 함께 커밋하거나 롤백한다.
 */
public interface InterestPaymentUseCase {

    InterestPaymentResult pay(InterestPaymentCommand command);
}
