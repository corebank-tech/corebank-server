package com.shinhan.corebank.customer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

// customer 테이블에 접근하는 Spring Data JPA Repository
public interface CustomerJpaRepository
        extends JpaRepository<CustomerJpaEntity, Long> {

    // 로그인 아이디로 고객 Entity 조회
    Optional<CustomerJpaEntity> findByUserId(String userId);

    // 로그인 상태 변경 중 같은 고객의 동시 수정을 직렬화
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select customer
            from CustomerJpaEntity customer
            where customer.customerId = :customerId
            """)
    Optional<CustomerJpaEntity> findByIdForUpdate(
            @Param("customerId") Long customerId
    );
}
