package com.shinhan.corebank.customer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("고객 도메인 단위 테스트")
class CustomerTest {

    @Test
    @DisplayName("로그인 실패 횟수가 허용 범위를 벗어나면 복원할 수 없다")
    void rejectOutOfRangeLoginFailureCount() {
        assertThatThrownBy(() -> restoreCustomer(6, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 실패 횟수는 0회 이상 5회 이하여야 합니다.");
    }

    @Test
    @DisplayName("로그인 실패 횟수와 잠금 상태가 일치하지 않으면 복원할 수 없다")
    void rejectInconsistentLoginState() {
        assertThatThrownBy(() -> restoreCustomer(5, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 실패 횟수와 계정 잠금 상태가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("로그인 IP가 45자이면 로그인 성공 상태를 기록한다")
    void recordLoginSuccessWithMaximumLengthIp() {
        Customer customer = restoreCustomer(2, false);
        LocalDateTime loginAt =
                LocalDateTime.of(2026, 8, 14, 10, 0);
        String loginIp = "a".repeat(45);

        customer.recordLoginSuccess(loginAt, loginIp);

        assertThat(customer.getLastLoginAt()).isEqualTo(loginAt);
        assertThat(customer.getLastLoginIp()).isEqualTo(loginIp);
        assertThat(customer.getLoginFailureCount()).isZero();
    }

    @Test
    @DisplayName("로그인 IP가 45자를 초과하면 상태를 변경하지 않는다")
    void rejectLoginIpExceedingMaximumLength() {
        Customer customer = restoreCustomer(2, false);

        assertThatThrownBy(() ->
                customer.recordLoginSuccess(
                        LocalDateTime.of(2026, 8, 14, 10, 0),
                        "a".repeat(46)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP는 45자를 초과할 수 없습니다.");

        assertThat(customer.getLastLoginAt()).isNull();
        assertThat(customer.getLastLoginIp()).isNull();
        assertThat(customer.getLoginFailureCount()).isEqualTo(2);
    }

    private Customer restoreCustomer(
            int loginFailureCount,
            boolean accountLocked
    ) {
        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        return Customer.restore(
                1L,
                "user01",
                "passwordHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user01@example.com",
                "01012345678",
                loginFailureCount,
                accountLocked,
                "OPENED_DATE_ASC",
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt
        );
    }
}
