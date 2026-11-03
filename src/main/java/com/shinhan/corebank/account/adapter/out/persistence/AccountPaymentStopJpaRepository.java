package com.shinhan.corebank.account.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountPaymentStopJpaRepository extends JpaRepository<AccountPaymentStopJpaEntity, Long> {

    Optional<AccountPaymentStopJpaEntity> findByAccountIdAndCaseId(Long accountId, Long caseId);

    List<AccountPaymentStopJpaEntity> findByAccountIdAndActiveTrueOrderByPaymentStopIdAsc(Long accountId);

    boolean existsByAccountIdAndCaseIdNot(Long accountId, Long caseId);
}
