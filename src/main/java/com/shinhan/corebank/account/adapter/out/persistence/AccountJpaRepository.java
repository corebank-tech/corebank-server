package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
    boolean existsByAccountNumber(String accountNumber);

    // 아이디 찾기의 계좌 소유권 확인에 필요한 계좌를 번호로 조회한다.
    Optional<AccountJpaEntity> findByAccountNumber(String accountNumber);

    List<AccountJpaEntity> findAllByCustomerId(Long customerId);

    Optional<AccountJpaEntity> findByAccountIdAndCustomerId(Long accountId, Long customerId);

    // 동일 계좌의 비밀번호 실패 횟수 갱신을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select account
            from AccountJpaEntity account
            where account.accountId = :accountId
              and account.customerId = :customerId
            """)
    Optional<AccountJpaEntity> findByAccountIdAndCustomerIdForUpdate(
            @Param("accountId") Long accountId, @Param("customerId") Long customerId);

    // 다른 도메인의 배치용 잔액 일괄 조회. 락을 잡지 않는다(조회만, 변경 없음).
    @Query(
            """
        SELECT a.accountId AS accountId, a.balance AS balance
        FROM AccountJpaEntity a
        WHERE a.accountId IN :accountIds
        """)
    List<AccountBalanceProjection> findBalancesByAccountIds(@Param("accountIds") Collection<Long> accountIds);

    // 전수 대사용 keyset 페이지. OFFSET 없이 PK 다음 지점부터 읽어 뒤 페이지도 느려지지 않는다. 락을 잡지 않는다.
    @Query(
            """
        SELECT a.accountId AS accountId, a.balance AS balance
        FROM AccountJpaEntity a
        WHERE a.accountId > :afterAccountId
        ORDER BY a.accountId
        """)
    List<AccountBalanceProjection> findBalancesAfter(@Param("afterAccountId") long afterAccountId, Pageable pageable);

    // 만기 후보를 계좌 ID 순으로 페이지 조회한다. 이 조회에서는 락을 잡지 않는다.
    @Query(
            """
        SELECT a.accountId
        FROM AccountJpaEntity a
        WHERE a.accountId > :afterAccountId
          AND a.status = :status
          AND a.accountType IN :accountTypes
          AND a.maturityDate <= :maturityBaseDate
        ORDER BY a.accountId
        """)
    List<Long> findMaturityCandidateIdsAfter(
            @Param("afterAccountId") long afterAccountId,
            @Param("maturityBaseDate") LocalDate maturityBaseDate,
            @Param("status") AccountStatus status,
            @Param("accountTypes") Collection<AccountType> accountTypes,
            Pageable pageable);

    // 상태 전이 직전에 최신 계좌 정보를 읽고 변경이 끝날 때까지 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM AccountJpaEntity a
        WHERE a.accountId = :accountId
        """)
    Optional<AccountJpaEntity> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    // 만기해지 사전 검증에 필요한 컬럼만 조회한다.
    // AccountJpaEntity를 영속성 컨텍스트에 올리지 않는다.
    @Query(
            """
    SELECT new com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot(
        a.accountId,
        a.productId,
        a.accountType,
        a.status,
        a.maturityDate,
        a.withdrawalRegistered
    )
    FROM AccountJpaEntity a
    WHERE a.accountId = :accountId
      AND a.customerId = :customerId
    """)
    Optional<MaturityAccountSnapshot> findMaturityAccountSnapshot(
            @Param("accountId") Long accountId, @Param("customerId") Long customerId);

    interface AccountBalanceProjection {
        Long getAccountId();

        Long getBalance();
    }
}
