package com.shinhan.corebank.otp.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 고객별 OTP 발급 잠금 (#580). 쓰기는 OtpIssueLockJpaRepository의 조건부 네이티브 문장만 쓴다.
@Entity
@Table(name = "otp_issue_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpIssueLockJpaEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "owner_id", nullable = false, columnDefinition = "CHAR(36)")
    private String ownerId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
