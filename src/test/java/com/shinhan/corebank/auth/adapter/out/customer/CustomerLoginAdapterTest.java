package com.shinhan.corebank.auth.adapter.out.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.application.port.out.LoginFailureUpdateResult;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.domain.model.LoginCustomer;
import com.shinhan.corebank.customer.api.CustomerAuthenticationData;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.LoginFailureState;
import com.shinhan.corebank.customer.api.LoginSuccessState;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import com.shinhan.corebank.customer.api.RecordLoginSuccessCommand;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("고객 로그인 어댑터 단위 테스트")
class CustomerLoginAdapterTest {

    @Mock
    private CustomerAuthenticationFacade customerAuthenticationFacade;

    private CustomerLoginAdapter adapter;

    // 실제 Mapper와 Mock Facade를 사용해 어댑터 구성
    @BeforeEach
    void setUp() {
        adapter = new CustomerLoginAdapter(customerAuthenticationFacade, new CustomerAuthenticationMapper());
    }

    // customer 공개 데이터를 auth 로그인 모델로 변환
    @Test
    @DisplayName("로그인 아이디로 LoginCustomer를 조회한다")
    void findByUserId() {
        CustomerAuthenticationData data = new CustomerAuthenticationData(1L, "user01", "passwordHash", "홍길동", 2, false);

        given(customerAuthenticationFacade.findByUserId("user01")).willReturn(Optional.of(data));

        Optional<LoginCustomer> result = adapter.findByUserId("user01");

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId()).isEqualTo(1L);
        assertThat(result.get().getUserId()).isEqualTo("user01");
        assertThat(result.get().getPasswordHash()).isEqualTo("passwordHash");
        assertThat(result.get().getUserName()).isEqualTo("홍길동");
        assertThat(result.get().getLoginFailureCount()).isEqualTo(2);
        assertThat(result.get().isAccountLocked()).isFalse();
    }

    // 조회 결과가 없으면 빈 Optional 반환
    @Test
    @DisplayName("존재하지 않는 고객이면 빈 결과를 반환한다")
    void findByUserIdNotFound() {
        given(customerAuthenticationFacade.findByUserId("unknown")).willReturn(Optional.empty());

        Optional<LoginCustomer> result = adapter.findByUserId("unknown");

        assertThat(result).isEmpty();
    }

    // 로그인 실패 상태를 customer command로 변환해 전달
    @Test
    @DisplayName("로그인 실패 결과를 customer 모듈에 전달한다")
    void recordLoginFailure() {
        given(customerAuthenticationFacade.updateLoginFailureState(new RecordLoginFailureCommand(1L)))
                .willReturn(new LoginFailureState(4, false));

        LoginFailureUpdateResult result = adapter.recordLoginFailure(1L);

        verify(customerAuthenticationFacade).updateLoginFailureState(new RecordLoginFailureCommand(1L));
        assertThat(result.errorCount()).isEqualTo(4);
        assertThat(result.accountLocked()).isFalse();
    }

    // 로그인 성공 상태를 customer command로 변환해 전달
    @Test
    @DisplayName("로그인 성공 결과를 customer 모듈에 전달한다")
    void recordLoginSuccess() {
        LocalDateTime loginAt = LocalDateTime.of(2026, 8, 12, 10, 0);
        RecordLoginSuccessCommand command = new RecordLoginSuccessCommand(1L, loginAt, "192.168.0.10");
        given(customerAuthenticationFacade.updateLoginSuccessState(command)).willReturn(LoginSuccessState.COMPLETED);

        LoginSuccessUpdateResult result = adapter.recordLoginSuccess(1L, loginAt, "192.168.0.10");

        verify(customerAuthenticationFacade).updateLoginSuccessState(command);
        assertThat(result).isEqualTo(LoginSuccessUpdateResult.COMPLETED);
    }

    // customer 모듈의 동시 잠금 결과를 auth 결과로 변환
    @Test
    @DisplayName("로그인 성공 처리 중 계정 잠금 결과를 변환한다")
    void recordLoginSuccessAccountLocked() {
        LocalDateTime loginAt = LocalDateTime.of(2026, 8, 12, 10, 0);
        RecordLoginSuccessCommand command = new RecordLoginSuccessCommand(1L, loginAt, "192.168.0.10");
        given(customerAuthenticationFacade.updateLoginSuccessState(command))
                .willReturn(LoginSuccessState.ACCOUNT_LOCKED);

        LoginSuccessUpdateResult result = adapter.recordLoginSuccess(1L, loginAt, "192.168.0.10");

        assertThat(result).isEqualTo(LoginSuccessUpdateResult.ACCOUNT_LOCKED);
    }
}
