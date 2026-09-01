package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountNumberSequenceJpaRepository extends JpaRepository<AccountNumberSequenceJpaEntity, Long> {

    // 입출금계좌용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        select sequence
        from AccountNumberSequenceJpaEntity sequence
        where sequence.bankCode = :bankCode
          and sequence.accountType = :accountType
          and sequence.productId is null
        """)
    Optional<AccountNumberSequenceJpaEntity> findDemandDepositForUpdate(
            @Param("bankCode") String bankCode, @Param("accountType") AccountType accountType);

    // 예적금계좌용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        select sequence
        from AccountNumberSequenceJpaEntity sequence
        where sequence.bankCode = :bankCode
          and sequence.accountType = :accountType
          and sequence.productId = :productId
        """)
    Optional<AccountNumberSequenceJpaEntity> findProductAccountForUpdate(
            @Param("bankCode") String bankCode,
            @Param("accountType") AccountType accountType,
            @Param("productId") Long productId);
}
