package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.AccountLastTransactionQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginStatusQueryServiceTest {

    @Mock
    CustomerPersistencePort customerPersistencePort;

    @Mock
    AccountLastTransactionQueryPort accountLastTransactionQueryPort;

    @InjectMocks
    LoginStatusQueryService service;

    @Test
    @DisplayName("고객의 직전 접속일시·접속IP와, 보유 계좌 중 가장 최근 거래일시를 함께 반환한다")
    void getLoginStatus_combinesPreviousLoginCurrentLoginIpAndLastTransactionAt() {
        LocalDateTime previousLoginAt = LocalDateTime.of(2026, 3, 5, 10, 0);
        LocalDateTime lastTransactionAt = LocalDateTime.of(2026, 3, 12, 15, 0);
        when(customerPersistencePort.findById(1L))
                .thenReturn(Optional.of(customerWith(1L, previousLoginAt, "203.245.11.87")));
        when(accountLastTransactionQueryPort.findLatestTransactionAt(1L))
                .thenReturn(Optional.of(lastTransactionAt));

        LoginStatusResult result = service.getLoginStatus(1L);

        assertThat(result.previousLoginAt()).isEqualTo(previousLoginAt);
        assertThat(result.currentLoginIp()).isEqualTo("203.245.11.87");
        assertThat(result.lastTransactionAt()).isEqualTo(lastTransactionAt);
    }

    @Test
    @DisplayName("직전 접속 기록이 없으면(첫 로그인) previousLoginAt은 null이다")
    void getLoginStatus_noPreviousLogin_previousLoginAtIsNull() {
        when(customerPersistencePort.findById(2L))
                .thenReturn(Optional.of(customerWith(2L, null, "1.1.1.1")));
        when(accountLastTransactionQueryPort.findLatestTransactionAt(2L))
                .thenReturn(Optional.empty());

        LoginStatusResult result = service.getLoginStatus(2L);

        assertThat(result.previousLoginAt()).isNull();
    }

    @Test
    @DisplayName("보유 계좌가 없거나 전부 거래 이력이 없으면 lastTransactionAt은 null이다")
    void getLoginStatus_noTransactions_lastTransactionAtIsNull() {
        when(customerPersistencePort.findById(3L))
                .thenReturn(Optional.of(customerWith(3L, null, "1.1.1.1")));
        when(accountLastTransactionQueryPort.findLatestTransactionAt(3L))
                .thenReturn(Optional.empty());

        LoginStatusResult result = service.getLoginStatus(3L);

        assertThat(result.lastTransactionAt()).isNull();
    }

    @Test
    @DisplayName("세션의 customerId에 해당하는 고객이 없으면 예외를 던진다")
    void getLoginStatus_customerNotFound_throwsIllegalStateException() {
        when(customerPersistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLoginStatus(999L))
                .isInstanceOf(IllegalStateException.class);
    }

    private Customer customerWith(Long customerId, LocalDateTime previousLoginAt, String lastLoginIp) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        return Customer.restore(
                customerId,
                "user" + customerId,
                "passwordHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user" + customerId + "@example.com",
                "01012345678",
                0,
                false,
                "OPENED_DATE_ASC",
                LocalDateTime.of(2026, 3, 10, 11, 0),
                lastLoginIp,
                previousLoginAt,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt
        );
    }
}
