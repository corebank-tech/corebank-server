package com.shinhan.corebank.customer.application.port.out;

import com.shinhan.corebank.customer.domain.model.Customer;

import java.util.Optional;

// customer 도메인과 영속성 어댑터 사이의 저장소 계약
public interface CustomerPersistencePort {

    // 로그인 아이디로 고객 조회
    Optional<Customer> findByUserId(String userId);

    // 고객 PK로 고객 조회
    Optional<Customer> findById(Long customerId);

    // 변경된 고객 상태 저장
    Customer save(Customer customer);
}