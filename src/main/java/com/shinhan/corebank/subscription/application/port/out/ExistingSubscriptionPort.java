package com.shinhan.corebank.subscription.application.port.out;

public interface ExistingSubscriptionPort {
    // 1인 1계좌 제한(PRD0301) 판정용. product.single_account_limit=TRUE인 상품에만 적용된다.
    // 구현체는 두 단계로 락을 건다(둘 다 필요 — 하나만 있으면 실제 동시성 테스트로 확인된
    // 함정에 빠진다):
    //   1) product 행에 비관적 락을 걸어 같은 productId로 동시에 들어온 다른 트랜잭션을 이
    //      트랜잭션이 끝날 때까지 대기시킨다(순서 직렬화). product_subscription 자체에 락을
    //      걸지 않는 이유: 아직 행이 없는 조합에 SELECT ... FOR UPDATE를 걸면 InnoDB gap lock이
    //      걸리는데, gap lock은 서로 다른 트랜잭션끼리 충돌하지 않아 두 트랜잭션 모두 통과해버린다
    //      — 그래서 반드시 항상 존재가 보장된 product 행을 락 대상으로 쓴다.
    //   2) 그 뒤 product_subscription 조회도 반드시 잠금 조회로 한다. 평범한 SELECT는 InnoDB
    //      REPEATABLE READ의 일관된 읽기라 이 트랜잭션의 첫 쿼리 시점 스냅샷에 묶이므로, 1)의
    //      락으로 순서를 직렬화해도 상대가 그 사이 커밋한 행을 여전히 못 본다. 잠금 조회는
    //      스냅샷을 무시하고 항상 최신 커밋 데이터를 읽는다.
    // 반드시 쓰기 트랜잭션이 이미 열려 있는 상태(ProductSubscriptionExecuteService.execute()의
    // @Transactional 내부)에서만 호출한다 — 트랜잭션 밖에서 호출하면 락이 리포지토리 메서드
    // 종료와 함께 즉시 풀려 보호 효과가 없다.
    boolean existsActiveSubscription(Long customerId, Long productId);
}
