package com.shinhan.corebank.common.authtoken;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비는 모두 "아직 안 썼고, 선점되지 않았고, 만료되지 않았다"는 조건부 UPDATE 한 번이다.
 * 영향받은 행이 1일 때만 성공이고, UPDATE가 잡은 행 잠금이 동시 소비를 한 건으로 줄인다.
 */
public interface AuthTokenJpaRepository extends JpaRepository<AuthTokenJpaEntity, Long> {

    // 엔티티가 아니라 값만 읽어 영속성 컨텍스트에 남은 소비 전 엔티티를 다시 보지 않는다.
    @Query(
            """
        SELECT t.payload FROM AuthTokenJpaEntity t
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    Optional<String> findUsablePayload(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("now") LocalDateTime now);

    @Query(
            """
        SELECT t.payload FROM AuthTokenJpaEntity t
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose
        """)
    Optional<String> findPayload(@Param("tokenHash") String tokenHash, @Param("purpose") AuthTokenPurpose purpose);

    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.consumedAt = :now
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    int consume(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.consumedAt = :now
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.payload = :payload
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    int consumeIfPayloadMatches(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("payload") String payload,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.consumedAt = :now
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.customerId = :customerId
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    int consumeIfCustomerMatches(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("customerId") Long customerId,
            @Param("now") LocalDateTime now);

    // 회원가입 전환의 원본 토큰을 먼저 잠근다. 유니크 키 전체(=)로 찾아야 간격 잠금 없이 행 하나만 잠긴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
        SELECT t FROM AuthTokenJpaEntity t
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    Optional<AuthTokenJpaEntity> lockUsable(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.claimId = :claimId
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose
           AND t.consumedAt IS NULL AND t.claimId IS NULL AND t.expiresAt > :now
        """)
    int claim(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("claimId") String claimId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.consumedAt = :now
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.claimId = :claimId
           AND t.consumedAt IS NULL AND t.expiresAt > :now
        """)
    int completeClaim(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("claimId") String claimId,
            @Param("now") LocalDateTime now);

    // 만료 시각은 건드리지 않아 남은 유효 시간 그대로 복구된다.
    @Modifying(flushAutomatically = true)
    @Query(
            """
        UPDATE AuthTokenJpaEntity t SET t.claimId = NULL
         WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.claimId = :claimId
           AND t.consumedAt IS NULL AND t.expiresAt > :now
        """)
    int releaseClaim(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") AuthTokenPurpose purpose,
            @Param("claimId") String claimId,
            @Param("now") LocalDateTime now);

    // 한 번에 다 지우면 긴 트랜잭션이 되어 토큰 소비 UPDATE를 오래 막으므로 나눠서 지운다.
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM auth_token WHERE expires_at < :now LIMIT :limit", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
