package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLockJpaRepository extends JpaRepository<ProductLockJpaEntity, Long> {

    // 1인1계좌 제한(PRD0301) 판정 직렬화용. product_id는 항상 존재가 보장된 행이므로(호출 전에
    // ProductQueryUseCase로 상품 존재를 이미 확인함) 이 SELECT는 실제 레코드 락을 잡는다 — InnoDB
    // gap lock과 달리 레코드 락은 다른 트랜잭션과 서로 배타적이라, 같은 product_id로 동시에 들어온
    // 다른 트랜잭션은 이 트랜잭션이 커밋/롤백할 때까지 진짜로 블로킹된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT p
            FROM ProductLockJpaEntity p
            WHERE p.productId = :productId
            """)
    Optional<ProductLockJpaEntity> findByIdForUpdate(@Param("productId") Long productId);
}
