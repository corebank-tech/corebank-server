package com.shinhan.corebank.customer.application.port.out;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// 관리자 고객 계정 운영(#449)의 조회 전용 계약. 쓰기 포트(CustomerPersistencePort)와 분리한다.
public interface CustomerAdminQueryPort {

    // 가입일 최신순, 같으면 PK 역순. unpaged면 조건에 맞는 전 건을 반환한다.
    Page<CustomerAdminView> search(CustomerSearchCondition condition, Pageable pageable);

    // 전체 조회 상한 판정용. 본문을 읽기 전에 건수만 센다.
    long count(CustomerSearchCondition condition);

    Optional<CustomerAdminView> findById(Long customerId);
}
