package com.shinhan.corebank.otp.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// verification_request의 OTP 행을 잠금 조회하고 갱신한다.
public interface OtpVerificationJpaRepository extends JpaRepository<OtpVerificationJpaEntity, String> {

    // 고객별 OTP 범위를 잠가 동시 발급으로 OTP 두 개가 활성화되는 것을 방지한다.
    // 새 OTP 발급 전에 고객의 모든 기존 활성 OTP를 즉시 만료시킨다.
    @Modifying(flushAutomatically = true)
    @Query(
            """
            update OtpVerificationJpaEntity request
            set request.expiresAt = :now
            where request.customerId = :customerId
              and request.purpose = 'OTP_TRANSACTION'
              and request.used = false
              and request.locked = false
              and request.expiresAt > :now
            """)
    int expireActiveRequests(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

    // 동일 OTP 요청의 동시 성공과 실패 횟수 유실을 막기 위해 행 잠금을 획득한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select request
            from OtpVerificationJpaEntity request
            where request.verificationRequestId = :requestId
              and request.purpose = 'OTP_TRANSACTION'
            """)
    Optional<OtpVerificationJpaEntity> findByIdForUpdate(@Param("requestId") String requestId);

    // 검증 성공한 OTP 요청을 인증 토큰의 거래내용 대조에 사용한다.
    @Query(
            """
            select request
            from OtpVerificationJpaEntity request
            where request.verificationRequestId = :requestId
              and request.purpose = 'OTP_TRANSACTION'
              and request.used = true
              and request.verifiedAt is not null
            """)
    Optional<OtpVerificationJpaEntity> findVerifiedById(@Param("requestId") String requestId);
}
