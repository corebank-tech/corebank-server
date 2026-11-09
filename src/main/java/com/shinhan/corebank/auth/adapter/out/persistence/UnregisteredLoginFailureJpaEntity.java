package com.shinhan.corebank.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 가입되지 않은 아이디의 로그인 실패 횟수 (PH-08). 쓰기는 UnregisteredLoginFailureJpaRepository의 upsert만 쓴다.
@Entity
@Table(name = "unregistered_login_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnregisteredLoginFailureJpaEntity {

    @Id
    @Column(name = "user_id_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String userIdHash;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
