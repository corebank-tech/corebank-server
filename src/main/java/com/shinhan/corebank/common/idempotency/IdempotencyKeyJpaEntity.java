package com.shinhan.corebank.common.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 멱등키 (common.md §7-2).
 *
 * request_hash 계산 시 *AuthToken 으로 끝나는 필드는 제외한다.
 * 네트워크 지연 재요청에서 토큰만 갱신돼 다른 요청으로 오탐지되는 것을 막기 위함.
 */
@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "idempotency_key", columnDefinition = "CHAR(36)")
    private String idempotencyKey;                  // UUID v4. 자동 생성 아님

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "endpoint", nullable = false, length = 120)
    private String endpoint;                        // 같은 키의 타 API 재사용 차단

    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;                     // SHA-256

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 12)
    private IdempotencyState state;

    @Column(name = "http_status")
    private Short httpStatus;                       // SMALLINT

    @JdbcTypeCode(SqlTypes.JSON)                    // Hibernate 6+ 기본 지원
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;                // 24시간 후 배치 삭제

    public static IdempotencyKeyJpaEntity start(
            String key, Long customerId, String endpoint, String requestHash, LocalDateTime now) {
        IdempotencyKeyJpaEntity e = new IdempotencyKeyJpaEntity();
        e.idempotencyKey = key;
        e.customerId = customerId;
        e.endpoint = endpoint;
        e.requestHash = requestHash;
        e.state = IdempotencyState.PROCESSING;
        e.createdAt = now;
        e.expiresAt = now.plusHours(24);
        return e;
    }

    public void complete(short httpStatus, String responseSnapshot) {
        if (this.state != IdempotencyState.PROCESSING) {
            throw new IllegalStateException("Idempotency key is already completed: " + this.idempotencyKey);
        }
        this.state = IdempotencyState.COMPLETED;
        this.httpStatus = httpStatus;
        this.responseSnapshot = responseSnapshot;
    }

    public boolean matches(String endpoint, String requestHash) {
        return this.endpoint.equals(endpoint) && this.requestHash.equals(requestHash);   // 불일치 → CMN0302
    }
}

