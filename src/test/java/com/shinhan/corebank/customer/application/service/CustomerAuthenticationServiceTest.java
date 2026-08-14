package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.CustomerAuthenticationData;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import com.shinhan.corebank.customer.api.RecordLoginSuccessCommand;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        given(customerPersistencePort.findById(1L))
                .willReturn(Optional.of(customer));

        service.updateLoginFailureState(
                new RecordLoginFailureCommand(1L)
        );

        ArgumentCaptor<Customer> captor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerPersistencePort).save(captor.capture());

        Customer savedCustomer = captor.getValue();

        assertThat(savedCustomer.getLoginFailureCount()).isEqualTo(5);
        assertThat(savedCustomer.isAccountLocked()).isTrue();
    }

    // 로그인 실패 시 현재 횟수에서 정확히 1회만 증가
    @Test
    @DisplayName("로그인 실패 시 실패 횟수를 1회 증가시킨다")
    void incrementLoginFailureCount() {
        Customer customer = createCustomer(1L, 3, false);

        given(customerPersistencePort.findById(1L))
                .willReturn(Optional.of(customer));

        service.updateLoginFailureState(
                new RecordLoginFailureCommand(1L)
        );

        ArgumentCaptor<Customer> captor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerPersistencePort).save(captor.capture());

        Customer savedCustomer = captor.getValue();

        assertThat(savedCustomer.getLoginFailureCount()).isEqualTo(4);
        assertThat(savedCustomer.isAccountLocked()).isFalse();
    }

    // 잠긴 계정은 로그인 실패 처리로 횟수 감소 또는 잠금 해제 불가
    @Test
    @DisplayName("잠긴 계정의 로그인 실패 상태는 변경할 수 없다")
    void rejectLoginFailureForLockedCustomer() {
        Customer customer = createCustomer(1L, 5, true);

        given(customerPersistencePort.findById(1L))
                .willReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                service.updateLoginFailureState(
                        new RecordLoginFailureCommand(1L)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잠긴 계정의 로그인 실패 상태는 변경할 수 없습니다.");

        assertThat(customer.getLoginFailureCount()).isEqualTo(5);
        assertThat(customer.isAccountLocked()).isTrue();
        verify(customerPersistencePort, never()).save(customer);
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

        given(customerPersistencePort.findById(1L))
                .willReturn(Optional.of(customer));

        service.updateLoginSuccessState(
                new RecordLoginSuccessCommand(
                        1L,
                        newLoginAt,
                        "192.168.0.10"
                )
        );

        ArgumentCaptor<Customer> captor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerPersistencePort).save(captor.capture());

        Customer savedCustomer = captor.getValue();

        assertThat(savedCustomer.getLoginFailureCount()).isZero();
        assertThat(savedCustomer.isAccountLocked()).isFalse();
        assertThat(savedCustomer.getPreviousLoginAt())
                .isEqualTo(previousLastLoginAt);
        assertThat(savedCustomer.getLastLoginAt())
                .isEqualTo(newLoginAt);
        assertThat(savedCustomer.getLastLoginIp())
                .isEqualTo("192.168.0.10");
    }

    // 상태 변경 대상 고객이 없으면 내부 정합성 예외 발생
    @Test
    @DisplayName("상태를 변경할 고객이 없으면 예외가 발생한다")
    void updateLoginFailureStateCustomerNotFound() {
        given(customerPersistencePort.findById(99L))
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
                "OPENED_DATE_ASC",
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
