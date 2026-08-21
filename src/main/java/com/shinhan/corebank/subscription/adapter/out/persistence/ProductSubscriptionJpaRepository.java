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

    // 1인1계좌 제한(PRD0301) 판정용. 반드시 잠금(PESSIMISTIC_WRITE) 조회로 한다 — 평범한 SELECT는
    // InnoDB REPEATABLE READ의 일관된 읽기(consistent read)라 이 트랜잭션의 첫 쿼리 시점 스냅샷에
    // 묶인다. ProductLockJpaRepository로 product 행 락을 잡아 순서를 직렬화해도, 뒤이은 조회가
    // 평범한 SELECT면 락이 풀리기 전(=상대가 커밋하기 전) 스냅샷을 계속 보게 되어 상대가 방금
    // 커밋한 행을 여전히 못 본다(실제 동시성 테스트로 확인된 함정). 잠금 조회는 스냅샷을 무시하고
    // 항상 최신 커밋 데이터를 읽으므로 이 문제가 없다.
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
