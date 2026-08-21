package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.CustomerAuthenticationData;
import com.shinhan.corebank.customer.api.LoginFailureState;
import com.shinhan.corebank.customer.api.LoginSuccessState;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import com.shinhan.corebank.customer.api.RecordLoginSuccessCommand;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("고객 인증정보 서비스 단위 테스트")
class CustomerAuthenticationServiceTest {

    @Mock
    private CustomerPersistencePort customerPersistencePort;

    @InjectMocks
    private CustomerAuthenticationService service;

    // 로그인 아이디로 고객 인증정보를 조회
    @Test
    @DisplayName("로그인 아이디로 고객 인증정보를 조회한다")
    void findByUserId() {
        Customer customer = createCustomer(1L, 2, false);

        given(customerPersistencePort.findByUserId("user01"))
                .willReturn(Optional.of(customer));

        Optional<CustomerAuthenticationData> result =
                service.findByUserId("user01");

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId()).isEqualTo(1L);
        assertThat(result.get().getUserId()).isEqualTo("user01");
        assertThat(result.get().getPasswordHash()).isEqualTo("passwordHash");
        assertThat(result.get().getUserName()).isEqualTo("홍길동");
        assertThat(result.get().getLoginFailureCount()).isEqualTo(2);
        assertThat(result.get().isAccountLocked()).isFalse();
    }

    // 존재하지 않는 로그인 아이디는 빈 Optional 반환
    @Test
    @DisplayName("존재하지 않는 로그인 아이디면 빈 결과를 반환한다")
    void findByUserIdNotFound() {
        given(customerPersistencePort.findByUserId("unknown"))
                .willReturn(Optional.empty());

        Optional<CustomerAuthenticationData> result =
                service.findByUserId("unknown");

        assertThat(result).isEmpty();
    }

    // 로그인 실패 횟수와 잠금 상태를 고객에게 반영해 저장
    @Test
    @DisplayName("5번째 로그인 실패 시 고객 계정을 잠그고 저장한다")
    void updateLoginFailureState() {
        Customer customer = createCustomer(1L, 4, false);

        given(customerPersistencePort.findByIdForUpdate(1L))
                .willReturn(Optional.of(customer));

        LoginFailureState result = service.updateLoginFailureState(
                new RecordLoginFailureCommand(1L)
        );

        verify(customerPersistencePort)
                .updateLoginFailureState(customer);
        assertThat(result.loginFailureCount()).isEqualTo(5);
        assertThat(result.accountLocked()).isTrue();
    }

    // 로그인 실패 시 현재 횟수에서 정확히 1회만 증가
    @Test
    @DisplayName("로그인 실패 시 실패 횟수를 1회 증가시킨다")
    void incrementLoginFailureCount() {
        Customer customer = createCustomer(1L, 3, false);

        given(customerPersistencePort.findByIdForUpdate(1L))
                .willReturn(Optional.of(customer));

        LoginFailureState result = service.updateLoginFailureState(
                new RecordLoginFailureCommand(1L)
        );

        verify(customerPersistencePort)
                .updateLoginFailureState(customer);
        assertThat(result.loginFailureCount()).isEqualTo(4);
        assertThat(result.accountLocked()).isFalse();
    }

    // 잠긴 계정은 상태를 변경하지 않고 최신 잠금 상태를 반환
    @Test
    @DisplayName("이미 잠긴 계정이면 저장하지 않고 최신 잠금 상태를 반환한다")
    void returnLockedStateForLockedCustomer() {
        Customer customer = createCustomer(1L, 5, true);

        given(customerPersistencePort.findByIdForUpdate(1L))
                .willReturn(Optional.of(customer));

        LoginFailureState result = service.updateLoginFailureState(
                new RecordLoginFailureCommand(1L)
        );

        assertThat(result.loginFailureCount()).isEqualTo(5);
        assertThat(result.accountLocked()).isTrue();
        assertThat(customer.getLoginFailureCount()).isEqualTo(5);
        assertThat(customer.isAccountLocked()).isTrue();
        verify(customerPersistencePort, never())
                .updateLoginFailureState(customer);
    }

    // 로그인 성공 시 실패 횟수를 초기화하고 접속 이력을 이동
    @Test
    @DisplayName("로그인 성공 시 실패 횟수와 최근 접속정보를 갱신한다")
    void updateLoginSuccessState() {
        LocalDateTime previousLastLoginAt =
                LocalDateTime.of(2026, 8, 10, 9, 0);

        LocalDateTime newLoginAt =
                LocalDateTime.of(2026, 8, 12, 10, 0);

        Customer customer = createCustomer(
                1L,
                3,
                false,
                previousLastLoginAt,
                "127.0.0.1",
                null
        );

        given(customerPersistencePort.findByIdForUpdate(1L))
                .willReturn(Optional.of(customer));

        LoginSuccessState result = service.updateLoginSuccessState(
                new RecordLoginSuccessCommand(
                        1L,
                        newLoginAt,
                        "192.168.0.10"
                )
        );

        verify(customerPersistencePort)
                .updateLoginSuccessState(customer);

        assertThat(result).isEqualTo(LoginSuccessState.COMPLETED);
        assertThat(customer.getLoginFailureCount()).isZero();
        assertThat(customer.isAccountLocked()).isFalse();
        assertThat(customer.getPreviousLoginAt())
                .isEqualTo(previousLastLoginAt);
        assertThat(customer.getLastLoginAt())
                .isEqualTo(newLoginAt);
        assertThat(customer.getLastLoginIp())
                .isEqualTo("192.168.0.10");
    }

    // 성공 처리 전에 잠긴 고객은 접속정보와 실패 횟수를 변경하지 않음
    @Test
    @DisplayName("로그인 성공 처리 전에 계정이 잠기면 상태를 저장하지 않는다")
    void returnLockedStateWhenAccountLocksBeforeLoginSuccess() {
        Customer customer = createCustomer(1L, 5, true);

        given(customerPersistencePort.findByIdForUpdate(1L))
                .willReturn(Optional.of(customer));

        LoginSuccessState result = service.updateLoginSuccessState(
                new RecordLoginSuccessCommand(
                        1L,
                        LocalDateTime.of(2026, 8, 12, 10, 0),
                        "192.168.0.10"
                )
        );

        assertThat(result).isEqualTo(LoginSuccessState.ACCOUNT_LOCKED);
        assertThat(customer.getLoginFailureCount()).isEqualTo(5);
        assertThat(customer.isAccountLocked()).isTrue();
        verify(customerPersistencePort, never())
                .updateLoginSuccessState(customer);
    }

    // 상태 변경 대상 고객이 없으면 내부 정합성 예외 발생
    @Test
    @DisplayName("상태를 변경할 고객이 없으면 예외가 발생한다")
    void updateLoginFailureStateCustomerNotFound() {
        given(customerPersistencePort.findByIdForUpdate(99L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateLoginFailureState(
                        new RecordLoginFailureCommand(99L)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로그인 상태를 변경할 고객이 존재하지 않습니다.");
    }

    // 테스트용 고객 도메인 모델 생성
    private Customer createCustomer(
            Long customerId,
            int loginFailureCount,
            boolean accountLocked
    ) {
        return createCustomer(
                customerId,
                loginFailureCount,
                accountLocked,
                null,
                null,
                null
        );
    }

    // 접속 이력을 포함한 테스트용 고객 도메인 모델 생성
    private Customer createCustomer(
            Long customerId,
            int loginFailureCount,
            boolean accountLocked,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            LocalDateTime previousLoginAt
    ) {
        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 1, 1, 9, 0);

        return Customer.restore(
                customerId,
                "user01",
                "passwordHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user01@example.com",
                "01012345678",
                loginFailureCount,
                accountLocked,
                lastLoginAt,
                lastLoginIp,
                previousLoginAt,
                joinedAt,
                joinedAt,
                joinedAt,
                joinedAt
        );
    }
}
