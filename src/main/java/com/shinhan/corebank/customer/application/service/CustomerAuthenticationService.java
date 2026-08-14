package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.*;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

// 고객 인증정보 조회와 로그인 상태 변경을 처리하는 application service
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAuthenticationService
        implements CustomerAuthenticationFacade {

    private final CustomerPersistencePort customerPersistencePort;

    // 로그인 아이디로 고객 인증정보 조회
    @Override
    public Optional<CustomerAuthenticationData> findByUserId(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return customerPersistencePort.findByUserId(userId)
                .map(this::toAuthenticationData);
    }

    // 로그인 실패 횟수와 계정 잠금 상태 저장
    @Override
    @Transactional
    public LoginFailureState updateLoginFailureState(
            RecordLoginFailureCommand command
    ) {
        Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Objects.requireNonNull(
                command.customerId(),
                "customerId must not be null"
        );

        Customer customer = customerPersistencePort
                .findByIdForUpdate(command.customerId())
                .orElseThrow(() -> new IllegalStateException(
                        "로그인 상태를 변경할 고객이 존재하지 않습니다."
                ));

        // 다른 요청이 먼저 잠근 계정이면 상태를 변경하지 않고 최신 상태를 반환
        if (customer.isAccountLocked()) {
            return new LoginFailureState(
                    customer.getLoginFailureCount(),
                    true
            );
        }

        customer.recordLoginFailure();

        customerPersistencePort.updateLoginFailureState(customer);

        return new LoginFailureState(
                customer.getLoginFailureCount(),
                customer.isAccountLocked()
        );
    }

    // 로그인 성공 시 실패 횟수를 초기화하고 최근 접속정보 저장
    @Override
    @Transactional
    public LoginSuccessState updateLoginSuccessState(
            RecordLoginSuccessCommand command
    ) {
        Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Objects.requireNonNull(
                command.customerId(),
                "customerId must not be null"
        );

        Customer customer = customerPersistencePort
                .findByIdForUpdate(command.customerId())
                .orElseThrow(() -> new IllegalStateException(
                        "로그인 상태를 변경할 고객이 존재하지 않습니다."
                ));

        // 비밀번호 검증 이후 다른 요청이 잠근 계정이면 성공 상태를 저장하지 않음
        if (customer.isAccountLocked()) {
            return LoginSuccessState.ACCOUNT_LOCKED;
        }

        customer.recordLoginSuccess(
                command.loginAt(),
                command.loginIp()
        );

        customerPersistencePort.updateLoginSuccessState(customer);

        return LoginSuccessState.COMPLETED;
    }

    // customer 도메인 모델을 로그인 인증용 공개 데이터로 변환
    private CustomerAuthenticationData toAuthenticationData(
            Customer customer
    ) {
        return new CustomerAuthenticationData(
                customer.getCustomerId(),
                customer.getUserId(),
                customer.getPasswordHash(),
                customer.getUserName(),
                customer.getLoginFailureCount(),
                customer.isAccountLocked()
        );
    }
}
