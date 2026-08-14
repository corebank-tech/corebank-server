package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.CustomerAuthenticationData;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import com.shinhan.corebank.customer.api.RecordLoginSuccessCommand;
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
    public void updateLoginFailureState(
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

        Customer customer = findCustomer(command.customerId());

        customer.recordLoginFailure();

        customerPersistencePort.save(customer);
    }

    // 로그인 성공 시 실패 횟수를 초기화하고 최근 접속정보 저장
    @Override
    @Transactional
    public void updateLoginSuccessState(
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

        Customer customer = findCustomer(command.customerId());

        customer.recordLoginSuccess(
                command.loginAt(),
                command.loginIp()
        );

        customerPersistencePort.save(customer);
    }

    // 로그인 상태를 변경할 고객을 PK로 조회
    private Customer findCustomer(Long customerId) {
        return customerPersistencePort.findById(customerId)
                .orElseThrow(() -> new IllegalStateException(
                        "로그인 상태를 변경할 고객이 존재하지 않습니다."
                ));
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
