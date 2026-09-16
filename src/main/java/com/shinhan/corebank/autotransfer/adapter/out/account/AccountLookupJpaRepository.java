package com.shinhan.corebank.autotransfer.adapter.out.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLookupJpaRepository extends JpaRepository<AccountLookupJpaEntity, Long> {
    Optional<AccountLookupJpaEntity> findByAccountNumber(String accountNumber);

    boolean existsByAccountIdAndCustomerId(Long accountId, Long customerId);
}
