package com.shinhan.corebank.signup.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// 이메일 인증 요청의 저장·잠금 조회·기존 요청 무효화를 담당한다.
public interface EmailVerificationJpaRepository
        extends JpaRepository<EmailVerificationJpaEntity, String> {

    // 동일 인증 요청의 동시 성공을 방지하기 위해 행 잠금을 획득한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from EmailVerificationJpaEntity request
            where request.verificationRequestId = :requestId
              and request.purpose in ('SIGN_UP', 'EMAIL_CHANGE')
            """)
    Optional<EmailVerificationJpaEntity> findByIdForUpdate(
            @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationJpaEntity request
            set request.used = true
            where request.target = :email
              and request.purpose = :purpose
              and request.used = false
            """)
    int invalidateActiveRequests(
            @Param("email") String email,
            @Param("purpose") String purpose
    );
}
