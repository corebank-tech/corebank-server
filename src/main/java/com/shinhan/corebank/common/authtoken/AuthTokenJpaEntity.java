package com.shinhan.corebank.common.authtoken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 일회성 인증 토큰 (#580). 원문 대신 SHA-256 해시를 저장한다.
@Entity
@Table(name = "auth_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_token_id")
    private Long authTokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private AuthTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "claim_id", columnDefinition = "CHAR(36)")
    private String claimId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    static AuthTokenJpaEntity issue(
            AuthTokenPurpose purpose,
            String tokenHash,
            Long customerId,
            String payload,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        AuthTokenJpaEntity entity = new AuthTokenJpaEntity();
        entity.purpose = purpose;
        entity.tokenHash = tokenHash;
        entity.customerId = customerId;
        entity.payload = payload;
        entity.createdAt = createdAt;
        entity.expiresAt = expiresAt;
        return entity;
    }
}
