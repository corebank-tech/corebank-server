package com.shinhan.corebank.limit.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferLimitJpaRepository extends JpaRepository<TransferLimitJpaEntity, Long> {

    /**
     * 한도 변경(REQ-TRSF-025) 경로에서 X-Lock 을 잡고 조회한다. 읽고-검사하고-쓰는 사이에
     * 다른 변경 요청이 끼어들지 못하게 막는다. 조회 API 는 이 메서드를 쓰지 않는다 - 락 없이
     * 스냅샷으로 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT l
        FROM TransferLimitJpaEntity l
        WHERE l.customerId = :customerId
        """)
    Optional<TransferLimitJpaEntity> findByCustomerIdForUpdate(@Param("customerId") Long customerId);

    /**
     * 이체 실행(REQ-TRSF-010·011) 경로에서 S-Lock 을 잡고 조회한다. 이체는 한도를 읽기만 하므로
     * 이체끼리는 서로 막지 않고, 한도 변경의 X-Lock 만 대기시킨다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT l
        FROM TransferLimitJpaEntity l
        WHERE l.customerId = :customerId
        """)
    Optional<TransferLimitJpaEntity> findByCustomerIdForShare(@Param("customerId") Long customerId);
}
