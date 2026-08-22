package com.shinhan.corebank.account.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
    boolean existsByAccountNumber(String accountNumber);

    List<AccountJpaEntity> findAllByCustomerId(Long customerId);

    Optional<AccountJpaEntity> findByAccountIdAndCustomerId(
            Long accountId,
            Long customerId
    );

    // 동일 계좌의 비밀번호 실패 횟수 갱신을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from AccountJpaEntity account
            where account.accountId = :accountId
              and account.customerId = :customerId
            """)
    Optional<AccountJpaEntity> findByAccountIdAndCustomerIdForUpdate(
            @Param("accountId") Long accountId,
            @Param("customerId") Long customerId
    );
}
