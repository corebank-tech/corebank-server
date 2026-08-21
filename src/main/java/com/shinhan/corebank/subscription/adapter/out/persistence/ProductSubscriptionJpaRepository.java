package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSubscriptionJpaRepository extends JpaRepository<ProductSubscriptionJpaEntity, Long> {
    Optional<ProductSubscriptionJpaEntity> findBySubscriptionIdAndCustomerId(Long subscriptionId, Long customerId);

    // 1인1계좌 제한(PRD0301) 판정용 락 조회. customer_id+product_id 인덱스 구간에 X-Lock(gap lock 포함)을
    // 걸어, 같은 조합으로 동시에 들어온 다른 트랜잭션의 INSERT를 이 트랜잭션이 끝날 때까지 대기시킨다.
    // status 조건은 일부러 걸지 않는다 — status까지 필터링하면 아직 SUCCESS 행이 없을 때 옵티마이저가
    // 더 좁은 구간만 잠글 수 있어 두 트랜잭션이 서로 다른 틈을 잡고 통과할 여지가 남는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM ProductSubscriptionJpaEntity s
            WHERE s.customerId = :customerId
              AND s.productId = :productId
            """)
    List<ProductSubscriptionJpaEntity> findAllByCustomerIdAndProductIdForUpdate(
            @Param("customerId") Long customerId,
            @Param("productId") Long productId);
}
