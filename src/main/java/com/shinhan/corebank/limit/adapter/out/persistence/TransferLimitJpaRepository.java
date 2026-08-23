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
     * <p>updateLimit 과 갈라 둔 이유는 두 경로의 의도가 반대이기 때문이다. 고객이 한도를 올린 뒤
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
     * 잠가 둔 행의 한도를 갱신한다. <b>단순 UPDATE 다</b> - 호출 전에
     * findForUpdateByCustomerId 로 행을 잠갔으므로 없는 행을 만들 일이 없다.
     *
     * <p>영향 행 수를 확인하지 않는 것은 그 트랜잭션이 X-Lock 을 쥐고 있어 그 사이 행이
     * 사라질 수 없기 때문이다. 당일 사용액의 findForUpdate 뒤 saveUsage 도 같다.
     *
     * <p>영속성 컨텍스트를 우회하는 네이티브라 @Modifying 으로 실행 전 flush·실행 후 clear 를
     * 걸고, JPA Auditing 을 타지 않으므로 updated_at 을 자바 Clock 에서 넘겨받는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE transfer_limit
           SET one_time_limit = :oneTimeLimit,
               daily_limit    = :dailyLimit,
               updated_at     = :now
         WHERE customer_id = :customerId
        """, nativeQuery = true)
    void updateLimit(@Param("customerId") Long customerId,
                     @Param("oneTimeLimit") long oneTimeLimit,
                     @Param("dailyLimit") long dailyLimit,
                     @Param("now") LocalDateTime now);
}
