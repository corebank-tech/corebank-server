package com.shinhan.corebank.auth.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnregisteredLoginFailureJpaRepository
        extends JpaRepository<UnregisteredLoginFailureJpaEntity, String> {

    // 조회 후 저장으로 나누면 동시 실패가 서로를 덮어쓰므로 한 문장으로 센다.
    // MySQL은 SET을 왼쪽부터 평가하므로 failure_count가 바뀌기 전의 expires_at으로 만료 여부를 본다.
    @Modifying
    @Query(
            value =
                    """
            INSERT INTO unregistered_login_failure (user_id_hash, failure_count, last_failed_at, expires_at)
            VALUES (:userIdHash, 1, :failedAt, :expiresAt) AS new
            ON DUPLICATE KEY UPDATE
                failure_count = IF(unregistered_login_failure.expires_at <= new.last_failed_at,
                                   1, unregistered_login_failure.failure_count + 1),
                last_failed_at = new.last_failed_at,
                expires_at = new.expires_at
            """,
            nativeQuery = true)
    int upsertFailure(
            @Param("userIdHash") String userIdHash,
            @Param("failedAt") LocalDateTime failedAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    // 엔티티가 아니라 값으로 읽어 영속성 컨텍스트에 남은 이전 엔티티의 영향을 받지 않는다.
    @Query(
            """
        SELECT u.failureCount
          FROM UnregisteredLoginFailureJpaEntity u
         WHERE u.userIdHash = :userIdHash AND u.expiresAt > :now
        """)
    Optional<Integer> findActiveFailureCount(@Param("userIdHash") String userIdHash, @Param("now") LocalDateTime now);

    // upsert 직후 같은 트랜잭션에서 방금 센 횟수를 읽는다. 행은 upsert가 만들었으므로 항상 있다.
    @Query("SELECT u.failureCount FROM UnregisteredLoginFailureJpaEntity u WHERE u.userIdHash = :userIdHash")
    int findFailureCount(@Param("userIdHash") String userIdHash);

    // 한 번에 다 지우면 긴 트랜잭션이 되어 실패 upsert를 오래 막으므로 나눠서 지운다.
    @Modifying
    @Query(value = "DELETE FROM unregistered_login_failure WHERE expires_at < :now LIMIT :limit", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
