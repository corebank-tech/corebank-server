package com.shinhan.corebank.limit.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferLimitJpaRepository extends JpaRepository<TransferLimitJpaEntity, Long> {

    /**
     * 한도 변경(REQ-TRSF-025) 경로에서 X-Lock 을 잡고 조회한다. 읽고-검사하고-쓰는 사이에
     * 다른 변경 요청이 끼어들지 못하게 막는다. 조회 API 는 이 메서드를 쓰지 않는다 - 락 없이
     * 스냅샷으로 읽는다.
     *
     * <p>ForUpdate 를 By 앞에 둔 것은 파생 쿼리 규칙 때문이다. Spring Data 는 find 와 By 사이
     * 단어를 무시하므로 여기서 잠금 의도를 이름 앞에 드러내면서도 별도 JPQL 이 필요 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransferLimitJpaEntity> findForUpdateByCustomerId(Long customerId);

    /**
     * 이체 실행(REQ-TRSF-010·011) 경로에서 S-Lock 을 잡고 조회한다. 이체는 한도를 읽기만 하므로
     * 이체끼리는 서로 막지 않고, 한도 변경의 X-Lock 만 대기시킨다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<TransferLimitJpaEntity> findForShareByCustomerId(Long customerId);

    /**
     * 한도 행이 없으면 정책 기본값으로 만들고, <b>이미 있으면 아무것도 바꾸지 않는다</b>.
     * 가입 시 기본값 부여(REQ-TRSF-029) 전용이다.
     *
     * <p>upsert 와 갈라 둔 이유는 두 경로의 의도가 반대이기 때문이다. 고객이 한도를 올린 뒤
     * 이 메서드가 다시 불려도 올린 값이 남아야 한다 - 하나로 합치면 기본값으로 되돌아간다.
     * 기존 고객 백필 마이그레이션이 NOT EXISTS 를 쓴 것과 같은 이유다.
     *
     * <p>ON DUPLICATE KEY UPDATE 뒤의 customer_id = customer_id 는 아무것도 바꾸지 않는다 -
     * 목적은 행을 존재하게 만드는 것뿐이다. transfer_limit_daily_usage 의 insertIfAbsent 와
     * 같은 형태다. 네이티브 INSERT 는 JPA Auditing 을 타지 않아 시각을 직접 채운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
        VALUES (:customerId, :oneTimeLimit, :dailyLimit, :now, :now)
        ON DUPLICATE KEY UPDATE customer_id = customer_id
        """, nativeQuery = true)
    void insertIfAbsent(@Param("customerId") Long customerId,
                        @Param("oneTimeLimit") long oneTimeLimit,
                        @Param("dailyLimit") long dailyLimit,
                        @Param("now") LocalDateTime now);

    /**
     * 한도를 새 값으로 <b>덮어쓴다</b>. 고객이 직접 바꾸는 경로 전용이다 - 가입 시 기본값 부여는
     * 기존 값을 지우면 안 되므로 insertIfAbsent 를 쓴다.
     *
     * <p><b>조회 후 저장하는 두 문장으로 바꾸면 안 된다</b> -
     * SELECT ... FOR UPDATE 는 없는 행을 잠그지 못해, 한도 행이 없는 고객
     * (LimitCommandService.update 의 폴백)에게 변경이 동시에 들어오면 둘 다 "없음"을 보고
     * INSERT 한다. TransferLimitSaveConcurrencyTest 가 지킨다.
     *
     * <p>네이티브라 두 가지를 직접 챙긴다 - JPA Auditing 을 타지 않아 시각을 자바 Clock 에서
     * 넘겨받고, 영속성 컨텍스트를 우회하므로 @Modifying 으로 실행 전 flush·실행 후 clear 를 건다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
        VALUES (:customerId, :oneTimeLimit, :dailyLimit, :now, :now)
        ON DUPLICATE KEY UPDATE one_time_limit = :oneTimeLimit,
                                daily_limit    = :dailyLimit,
                                updated_at     = :now
        """, nativeQuery = true)
    void upsert(@Param("customerId") Long customerId,
                @Param("oneTimeLimit") long oneTimeLimit,
                @Param("dailyLimit") long dailyLimit,
                @Param("now") LocalDateTime now);
}
