package com.shinhan.corebank.autotransfer.adapter.out.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountLookupJpaRepository extends JpaRepository<AccountLookupJpaEntity, Long> {
    Optional<AccountLookupJpaEntity> findByAccountNumber(String accountNumber);
    boolean existsByAccountIdAndCustomerId(Long accountId, Long customerId);
}
