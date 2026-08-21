package com.shinhan.corebank.subscription.application.port.out;

public interface ExistingSubscriptionPort {
    // 1인 1계좌 제한(PRD0301) 판정용. product.single_account_limit=TRUE인 상품에만 적용된다.
    // 구현체가 비관적 락(PESSIMISTIC_WRITE) 조회를 쓰므로, 반드시 쓰기 트랜잭션이 이미 열려 있는
    // 상태(ProductSubscriptionExecuteService.execute()의 @Transactional 내부)에서만 호출한다.
    // 트랜잭션 밖에서 호출하면 락이 리포지토리 메서드 종료와 함께 즉시 풀려 보호 효과가 없다.
    boolean existsActiveSubscription(Long customerId, Long productId);
}
