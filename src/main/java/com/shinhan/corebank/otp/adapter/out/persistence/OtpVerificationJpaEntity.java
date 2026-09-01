package com.shinhan.corebank.otp.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 기존 verification_request 테이블의 OTP 관련 컬럼을 매핑한다.
@Entity
@Table(name = "verification_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class OtpVerificationJpaEntity {

    @Id
    @Column(name = "verification_request_id", length = 64)
    private String verificationRequestId;

    @Column(name = "purpose", nullable = false, length = 24)
    private String purpose;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;

    @Column(name = "transaction_type", length = 32)
    private String transactionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_data")
    private Map<String, Object> transactionData;

    @Column(name = "error_count", nullable = false)
    private byte errorCount;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "verified_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
