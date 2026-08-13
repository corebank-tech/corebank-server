package com.shinhan.corebank.account.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
    List<AccountJpaEntity> findAllByCustomerId(Long customerId);
}