package com.shinhan.corebank.signup.adapter.out.customer;

import com.shinhan.corebank.customer.api.CustomerRegistrationQuery;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// signup의 중복조회 포트를 customer 공개 API에 연결한다.
@Component
@RequiredArgsConstructor
public class SignupCustomerAvailabilityAdapter
        implements SignupCustomerAvailabilityPort {

    private final CustomerRegistrationQuery customerRegistrationQuery;

    @Override
    public boolean isUserIdTaken(String userId) {
        return customerRegistrationQuery.existsByUserId(userId);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return customerRegistrationQuery.existsByEmail(email);
    }
}
