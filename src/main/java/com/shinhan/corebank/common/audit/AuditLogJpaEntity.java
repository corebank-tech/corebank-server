package com.shinhan.corebank.common.audit;

import com.shinhan.corebank.common.util.MaskingUtil;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private Map<String, Object> detail;                          // 마스킹 후 저장

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    public static final Set<String> FORBIDDEN_DETAIL_KEY = Set.of("birthDate", "otp", "password");

    public static final String ACCOUNT_NUMBER_KEY_SUFFIX = "AccountNumber";

    public static AuditLogJpaEntity of(Long customerId, String transactionNumber,
                                       AuditEventType eventType, String requestIp,
                                       boolean success, Map<String, Object> detail, LocalDateTime now) {
        boolean hasTransactionNumber = transactionNumber != null && !transactionNumber.isBlank();
        if (eventType.isLedgerChanging() && !hasTransactionNumber) {
            throw new IllegalArgumentException(
                    "원장 변경 이벤트(%s)는 transactionNumber 가 반드시 있어야 합니다.".formatted(eventType));
        }
        if (!eventType.isLedgerChanging() && hasTransactionNumber) {
            throw new IllegalArgumentException(
                    "원장 비변경 이벤트(%s)는 transactionNumber 가 없어야 합니다.".formatted(eventType));
        }

        AuditLogJpaEntity e = new AuditLogJpaEntity();
        e.customerId = customerId;
        e.transactionNumber = transactionNumber;
        e.eventType = eventType.name();
        e.requestIp = requestIp;
        e.result = success ? "SUCCESS" : "FAILURE";
        e.detail = sanitizeDetail(detail);
        e.requestedAt = now;
        return e;
    }
    private static Map<String, Object> sanitizeDetail(Map<String, Object> detail) {
        if(detail == null) {return null;}
        Map<String, Object> sanitized = new HashMap<>();
        for(Map.Entry<String, Object> entry : detail.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if(FORBIDDEN_DETAIL_KEY.contains(key)) {
                throw new IllegalArgumentException("detail에 금지된 키가 포함되어 있습니다: " + key);
            }
            if(key.toLowerCase().endsWith(ACCOUNT_NUMBER_KEY_SUFFIX.toLowerCase())) {
                sanitized.put(key, MaskingUtil.maskAccountNumber((String)value));
            } else {
                sanitized.put(key, sanitizeValue(value));
            }
        }
        return sanitized;
    }

    // 재귀
    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitizeDetail((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AuditLogJpaEntity::sanitizeValue).toList();
        }
        return value;
    }
}