package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
// customer 테이블의 전체 컬럼을 매핑하는 JPA Entity
public class CustomerJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "user_id", nullable = false, unique = true, length = 20)
    private String userId;

    @Column(
            name = "password_hash",
            nullable = false,
            columnDefinition = "CHAR(60)"
    )
    private String passwordHash;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Column(
            name = "login_failure_count",
            nullable = false,
            columnDefinition = "TINYINT"
    )
    private int loginFailureCount;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked;

    @Column(name = "last_login_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "previous_login_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime previousLoginAt;

    @Column(name = "password_changed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime passwordChangedAt;

    @Column(
            name = "joined_at",
            nullable = false,
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime joinedAt;

    // 로그인 실패 횟수와 계정 잠금 상태만 갱신
    public void updateLoginFailureState(
            int loginFailureCount,
            boolean accountLocked
    ) {
        this.loginFailureCount = loginFailureCount;
        this.accountLocked = accountLocked;
    }

    // 로그인 성공 시각과 접속 IP 및 실패 횟수만 갱신
    public void updateLoginSuccessState(
            LocalDateTime previousLoginAt,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            int loginFailureCount
    ) {
        this.previousLoginAt = previousLoginAt;
        this.lastLoginAt = lastLoginAt;
        this.lastLoginIp = lastLoginIp;
        this.loginFailureCount = loginFailureCount;
    }

    // 고객정보 변경 대상인 휴대폰 번호와 이메일만 갱신한다.
    public void updateContactInfo(String phoneNumber, String email) {
        this.phoneNumber = phoneNumber;
        this.email = email;
    }
}
