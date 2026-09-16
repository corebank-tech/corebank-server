package com.shinhan.corebank.limit.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferLimitDailyUsageJpaRepository
        extends JpaRepository<TransferLimitDailyUsageJpaEntity, TransferLimitDailyUsageId> {

    /**
     * 당일 사용액 행이 없으면 0 으로 만든다. 이미 있으면 아무것도 바꾸지 않는다.
     *
     * <p>SELECT ... FOR UPDATE 는 없는 행을 잠그지 못하고 0 건을 돌려준다. 그날 첫 이체가 동시에
     * 들어오면 두 트랜잭션이 모두 "없음"을 확인하고 INSERT 해 Duplicate entry 가 난다.
     *
     * <p>중복 예외를 잡아 재조회하는 방법은 쓸 수 없다. JPA 스펙상 NoResultException 등 네 가지를
     * 뺀 모든 PersistenceException 은 현재 트랜잭션을 rollback-only 로 마킹해서, 예외를 잡아도
     * 그 트랜잭션 안에서는 복구할 수 없다. 이 메서드는 이체 트랜잭션 안에서 호출되므로 한도 행
     * 하나 만들려다 이체·원장·잔액이 통째로 롤백된다.
     *
     * <p>원자적 삽입은 DB 만 보장할 수 있어 upsert 를 쓴다. ON DUPLICATE KEY UPDATE 뒤의
     * customer_id = customer_id 는 아무것도 바꾸지 않는다 - 목적은 행을 존재하게 만드는 것뿐이다.
     * 네이티브 INSERT 는 JPA Auditing 을 타지 않아 created_at·updated_at 을 직접 채운다.
     */
    @Modifying
    @Query(
            value =
                    """
        INSERT INTO transfer_limit_daily_usage
               (customer_id, usage_date, used_amount, created_at, updated_at)
        VALUES (:customerId, :usageDate, 0, :now, :now)
        ON DUPLICATE KEY UPDATE customer_id = customer_id
        """,
            nativeQuery = true)
    void insertIfAbsent(
            @Param("customerId") Long customerId,
            @Param("usageDate") LocalDate usageDate,
            @Param("now") LocalDateTime now);

    /** 사용액을 증가시키기 전에 X-Lock 을 잡고 읽는다. insertIfAbsent 로 행을 보장한 뒤 호출한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        SELECT u
        FROM TransferLimitDailyUsageJpaEntity u
        WHERE u.customerId = :customerId AND u.usageDate = :usageDate
        """)
    Optional<TransferLimitDailyUsageJpaEntity> findForUpdate(
            @Param("customerId") Long customerId, @Param("usageDate") LocalDate usageDate);
}
