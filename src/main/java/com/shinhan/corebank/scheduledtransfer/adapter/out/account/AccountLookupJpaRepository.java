package com.shinhan.corebank.scheduledtransfer.adapter.out.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 빈 이름 명시: autotransfer.adapter.out.account.AccountLookupJpaRepository와 클래스 단순이름이 같아
// 기본 빈 이름(accountLookupJpaRepository)이 충돌한다.
@Repository("scheduledTransferAccountLookupJpaRepository")
public interface AccountLookupJpaRepository extends JpaRepository<AccountLookupJpaEntity, Long> {
    Optional<AccountLookupJpaEntity> findByAccountNumber(String accountNumber);

    boolean existsByAccountIdAndCustomerId(Long accountId, Long customerId);
}
