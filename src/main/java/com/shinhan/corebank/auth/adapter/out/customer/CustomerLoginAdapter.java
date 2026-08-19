package com.shinhan.corebank.auth.adapter.out.customer;

import com.shinhan.corebank.auth.application.port.out.LoginCustomerPort;
import com.shinhan.corebank.auth.application.port.out.LoginFailureUpdateResult;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.domain.model.LoginCustomer;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.LoginFailureState;
import com.shinhan.corebank.customer.api.LoginSuccessState;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import com.shinhan.corebank.customer.api.RecordLoginSuccessCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

// auth의 고객 인증정보 요청을 customer 공개 API로 연결하는 어댑터
@Component
@RequiredArgsConstructor
public class CustomerLoginAdapter implements LoginCustomerPort {

    private final CustomerAuthenticationFacade customerAuthenticationFacade;
    private final CustomerAuthenticationMapper customerAuthenticationMapper;

    // customer 모듈에서 로그인 아이디로 인증정보 조회
    @Override
    public Optional<LoginCustomer> findByUserId(String userId) {
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        return customerAuthenticationFacade.findByUserId(userId)
                .map(customerAuthenticationMapper::toLoginCustomer);
    }

    // 로그인 실패 결과를 customer 모듈에 전달
    @Override
    public LoginFailureUpdateResult recordLoginFailure(Long customerId) {
        Objects.requireNonNull(
                customerId,
                "customerId must not be null"
        );

        LoginFailureState state =
                customerAuthenticationFacade.updateLoginFailureState(
                        new RecordLoginFailureCommand(customerId)
                );

        return new LoginFailureUpdateResult(
                state.loginFailureCount(),
                state.accountLocked()
        );
    }

    // 로그인 성공 결과와 접속정보를 customer 모듈에 전달
    @Override
    public LoginSuccessUpdateResult recordLoginSuccess(
            Long customerId,
            LocalDateTime loginAt,
            String loginIp
    ) {
        Objects.requireNonNull(
                customerId,
                "customerId must not be null"
        );
        Objects.requireNonNull(
                loginAt,
                "loginAt must not be null"
        );
        Objects.requireNonNull(
                loginIp,
                "loginIp must not be null"
        );

        RecordLoginSuccessCommand command =
                new RecordLoginSuccessCommand(
                        customerId,
                        loginAt,
                        loginIp
                );

        LoginSuccessState state =
                customerAuthenticationFacade.updateLoginSuccessState(command);

        return switch (state) {
            case COMPLETED -> LoginSuccessUpdateResult.COMPLETED;
            case ACCOUNT_LOCKED ->
                    LoginSuccessUpdateResult.ACCOUNT_LOCKED;
        };
    }
}
