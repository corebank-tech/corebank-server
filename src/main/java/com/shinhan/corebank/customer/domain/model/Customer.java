package com.shinhan.corebank.customer.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// 고객 기본정보와 로그인 상태를 관리하는 도메인 모델
public class Customer {

    private static final int MAX_LOGIN_FAILURE_COUNT = 5;
    private static final int MAX_LOGIN_IP_LENGTH = 45;

    private final Long customerId;
    private final String userId;
    private String passwordHash;
    private String userName;
    private final LocalDate birthDate;
    private String email;
    private String phoneNumber;
    private int loginFailureCount;
    private boolean accountLocked;
    private String displayOrderType;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime previousLoginAt;
    private LocalDateTime passwordChangedAt;
    private final LocalDateTime joinedAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 영속 상태를 불변식 검증 후 도메인 모델로 복원
    public static Customer restore(
            Long customerId,
            String userId,
            String passwordHash,
            String userName,
            LocalDate birthDate,
            String email,
            String phoneNumber,
            int loginFailureCount,
            boolean accountLocked,
            String displayOrderType,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            LocalDateTime previousLoginAt,
            LocalDateTime passwordChangedAt,
            LocalDateTime joinedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        validateLoginState(loginFailureCount, accountLocked);

        return new Customer(
                customerId,
                userId,
                passwordHash,
                userName,
                birthDate,
                email,
                phoneNumber,
                loginFailureCount,
                accountLocked,
                displayOrderType,
                lastLoginAt,
                lastLoginIp,
                previousLoginAt,
                passwordChangedAt,
                joinedAt,
                createdAt,
                updatedAt
        );
    }

    // 로그인 실패 횟수를 1회 증가시키고 최대 허용 횟수 도달 시 계정을 잠금
    public void recordLoginFailure() {
        if (accountLocked) {
            throw new IllegalStateException(
                    "잠긴 계정의 로그인 실패 상태는 변경할 수 없습니다."
            );
        }

        this.loginFailureCount++;
        this.accountLocked =
                this.loginFailureCount == MAX_LOGIN_FAILURE_COUNT;
    }

    // 로그인 성공 시 실패 횟수를 초기화하고 접속정보를 갱신
    public void recordLoginSuccess(
            LocalDateTime loginAt,
            String loginIp
    ) {
        if (accountLocked) {
            throw new IllegalStateException("잠긴 계정은 로그인 성공 처리할 수 없습니다.");
        }
        if (loginAt == null) {
            throw new IllegalArgumentException("로그인 일시는 필수입니다.");
        }
        if (loginIp == null || loginIp.isBlank()) {
            throw new IllegalArgumentException("로그인 IP는 필수입니다.");
        }
        if (loginIp.length() > MAX_LOGIN_IP_LENGTH) {
            throw new IllegalArgumentException("로그인 IP는 45자를 초과할 수 없습니다.");
        }

        this.previousLoginAt = this.lastLoginAt;
        this.lastLoginAt = loginAt;
        this.lastLoginIp = loginIp;
        this.loginFailureCount = 0;
    }

    private static void validateLoginState(
            int loginFailureCount,
            boolean accountLocked
    ) {
        if (loginFailureCount < 0
                || loginFailureCount > MAX_LOGIN_FAILURE_COUNT) {
            throw new IllegalArgumentException(
                    "로그인 실패 횟수는 0회 이상 %d회 이하여야 합니다."
                            .formatted(MAX_LOGIN_FAILURE_COUNT)
            );
        }

        if (accountLocked
                != (loginFailureCount == MAX_LOGIN_FAILURE_COUNT)) {
            throw new IllegalArgumentException(
                    "로그인 실패 횟수와 계정 잠금 상태가 일치하지 않습니다."
            );
        }
    }

}
