package com.shinhan.corebank.account.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
    boolean existsByAccountNumber(String accountNumber);

    List<AccountJpaEntity> findAllByCustomerId(Long customerId);

    Optional<AccountJpaEntity> findByAccountIdAndCustomerId(
            Long accountId,
            Long customerId
    );
}
