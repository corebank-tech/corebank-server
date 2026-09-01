package com.shinhan.corebank.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        LocalDateTime loginAt = LocalDateTime.of(2026, 8, 14, 10, 0);
        String loginIp = "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255";

        customer.recordLoginSuccess(loginAt, loginIp);

        assertThat(customer.getLastLoginAt()).isEqualTo(loginAt);
        assertThat(customer.getLastLoginIp()).isEqualTo(loginIp);
        assertThat(customer.getLoginFailureCount()).isZero();
    }

    @Test
    @DisplayName("로그인 IP가 45자를 초과하면 상태를 변경하지 않는다")
    void rejectLoginIpExceedingMaximumLength() {
        Customer customer = restoreCustomer(2, false);

        assertThatThrownBy(() -> customer.recordLoginSuccess(LocalDateTime.of(2026, 8, 14, 10, 0), "a".repeat(46)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP는 45자를 초과할 수 없습니다.");

        assertThat(customer.getLastLoginAt()).isNull();
        assertThat(customer.getLastLoginIp()).isNull();
        assertThat(customer.getLoginFailureCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999.999.999.999", "not-an-ip", "203.0.113.10:8080", "2001:db8::gg"})
    @DisplayName("IP 형식이 올바르지 않으면 로그인 성공 상태를 기록하지 않는다")
    void rejectInvalidIpAddress(String invalidIpAddress) {
        Customer customer = restoreCustomer(2, false);

        assertThatThrownBy(() -> customer.recordLoginSuccess(LocalDateTime.of(2026, 8, 14, 10, 0), invalidIpAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP 형식이 올바르지 않습니다.");

        assertThat(customer.getLastLoginAt()).isNull();
        assertThat(customer.getLastLoginIp()).isNull();
        assertThat(customer.getLoginFailureCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("IPv6 형식의 로그인 IP를 기록한다")
    void recordLoginSuccessWithIpv6Address() {
        Customer customer = restoreCustomer(2, false);
        LocalDateTime loginAt = LocalDateTime.of(2026, 8, 14, 10, 0);

        customer.recordLoginSuccess(loginAt, "2001:db8::1");

        assertThat(customer.getLastLoginAt()).isEqualTo(loginAt);
        assertThat(customer.getLastLoginIp()).isEqualTo("2001:db8::1");
        assertThat(customer.getLoginFailureCount()).isZero();
    }

    @Test
    @DisplayName("검증된 휴대폰 번호와 이메일로 고객 연락처를 변경한다")
    void changesContactInfo() {
        Customer customer = restoreCustomer(0, false);
        LocalDateTime originalUpdatedAt = customer.getUpdatedAt();

        customer.changeContactInfo("01087654321", "newmail@corebank.com");

        assertThat(customer.getPhoneNumber()).isEqualTo("01087654321");
        assertThat(customer.getEmail()).isEqualTo("newmail@corebank.com");
        assertThat(customer.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("연락처 변경 필수값이 없으면 고객 상태를 변경하지 않는다")
    void rejectsMissingContactInfo() {
        Customer customer = restoreCustomer(0, false);

        assertThatThrownBy(() -> customer.changeContactInfo(null, "newmail@corebank.com"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(customer.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(customer.getEmail()).isEqualTo("user01@example.com");
    }

    private Customer restoreCustomer(int loginFailureCount, boolean accountLocked) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 9, 0);

        return Customer.restore(
                1L,
                "user01",
                null,
                "passwordHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user01@example.com",
                "01012345678",
                loginFailureCount,
                accountLocked,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt);
    }
}
