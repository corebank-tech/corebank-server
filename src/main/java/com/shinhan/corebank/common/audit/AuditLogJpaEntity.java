package com.shinhan.corebank.common.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 감사 로그 (REQ-NFR-010).
 * 원장 변경 거래는 고객ID·요청일시·IP·거래유형·거래번호·결과를 기록해야 한다.
 *
 * ⚠️ detail 에 비밀번호·OTP·전체 계좌번호·생년월일 평문 기록 금지 (REQ-NFR-009)
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long auditLogId;

    @Column(name = "customer_id")
    private Long customerId;                        // 비로그인 시도는 NULL

    @Column(name = "transaction_number", columnDefinition = "CHAR(20)")
    private String transactionNumber;               // 거래와 로그를 잇는 키

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "request_ip", nullable = false, length = 45)
    private String requestIp;                       // IPv6 대응

    @Column(name = "result", nullable = false, length = 12)
    private String result;                          // SUCCESS / FAILURE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;                          // 마스킹 후 저장

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    public static AuditLogJpaEntity of(Long customerId, String transactionNumber,
                                       AuditEventType eventType, String requestIp,
                                       boolean success, String detail, LocalDateTime now) {
        AuditLogJpaEntity e = new AuditLogJpaEntity();
        e.customerId = customerId;
        e.transactionNumber = transactionNumber;
        e.eventType = eventType.name();
        e.requestIp = requestIp;
        e.result = success ? "SUCCESS" : "FAILURE";
        e.detail = detail;
        e.requestedAt = now;
        return e;
    }
}