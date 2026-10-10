package com.shinhan.corebank.otp.adapter.out.persistence;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문장마다 자기 트랜잭션으로 바로 커밋한다.
 *
 * 둘을 한 트랜잭션에 묶으면 중복 INSERT IGNORE가 남긴 공유 잠금을 쥔 채 서로의 UPDATE를 기다리는 교착이 생긴다.
 */
public interface OtpIssueLockJpaRepository extends JpaRepository<OtpIssueLockJpaEntity, Long> {

    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT IGNORE INTO otp_issue_lock (customer_id, owner_id, expires_at)
            VALUES (:customerId, :ownerId, :expiresAt)
            """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("customerId") Long customerId,
            @Param("ownerId") String ownerId,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE otp_issue_lock SET owner_id = :ownerId, expires_at = :expiresAt
             WHERE customer_id = :customerId AND expires_at <= :now
            """,
            nativeQuery = true)
    int takeOverIfExpired(
            @Param("customerId") Long customerId,
            @Param("ownerId") String ownerId,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(
            value = "DELETE FROM otp_issue_lock WHERE customer_id = :customerId AND owner_id = :ownerId",
            nativeQuery = true)
    int deleteByOwner(@Param("customerId") Long customerId, @Param("ownerId") String ownerId);
}
