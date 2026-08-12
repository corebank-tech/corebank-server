package com.shinhan.corebank.customer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// customer 테이블에 접근하는 Spring Data JPA Repository
public interface CustomerJpaRepository
        extends JpaRepository<CustomerJpaEntity, Long> {

    // 로그인 아이디로 고객 Entity 조회
    Optional<CustomerJpaEntity> findByUserId(String userId);
}