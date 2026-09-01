package com.shinhan.corebank.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    /**
     * PROCESSING 상태일 때만 COMPLETED로 조건부 갱신한다.
     * 갱신 건수가 0이면 이미 다른 워커가 먼저 완료했거나 재시도 중인 것이므로,
     * 호출부는 레코드를 다시 읽어 저장된 응답을 재생하거나 처리 중 응답을 반환해야 한다.
     */
    @Modifying
    @Query(
            """
        UPDATE IdempotencyKeyJpaEntity e
           SET e.state = com.shinhan.corebank.common.idempotency.IdempotencyState.COMPLETED,
               e.httpStatus = :httpStatus,
               e.responseSnapshot = :responseSnapshot
         WHERE e.idempotencyKey = :key
           AND e.state = com.shinhan.corebank.common.idempotency.IdempotencyState.PROCESSING
        """)
    int completeIfProcessing(
            @Param("key") String key,
            @Param("httpStatus") short httpStatus,
            @Param("responseSnapshot") String responseSnapshot);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        UPDATE IdempotencyKeyJpaEntity e
           SET e.customerId = :customerId,
               e.state = com.shinhan.corebank.common.idempotency.IdempotencyState.COMPLETED,
               e.httpStatus = :httpStatus,
               e.responseSnapshot = :responseSnapshot
         WHERE e.idempotencyKey = :key
           AND e.state = com.shinhan.corebank.common.idempotency.IdempotencyState.PROCESSING
           AND e.customerId IS NULL
        """)
    int completeAnonymousIfProcessing(
            @Param("key") String key,
            @Param("customerId") Long customerId,
            @Param("httpStatus") short httpStatus,
            @Param("responseSnapshot") String responseSnapshot);
}
