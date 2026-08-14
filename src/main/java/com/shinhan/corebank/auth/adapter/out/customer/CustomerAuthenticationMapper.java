package com.shinhan.corebank.auth.adapter.out.customer;

import com.shinhan.corebank.auth.domain.model.LoginCustomer;
import com.shinhan.corebank.customer.api.CustomerAuthenticationData;
import org.springframework.stereotype.Component;

import java.util.Objects;

// customer 공개 데이터와 auth 로그인 모델 사이의 변환 담당
@Component
public class CustomerAuthenticationMapper {

    // customer 공개 데이터를 auth 로그인 모델로 변환
    public LoginCustomer toLoginCustomer(
            CustomerAuthenticationData data
    ) {
        Objects.requireNonNull(
                data,
                "data must not be null"
        );

        return new LoginCustomer(
                data.getCustomerId(),
                data.getUserId(),
                data.getPasswordHash(),
                data.getUserName(),
                data.getLoginFailureCount(),
                data.isAccountLocked()
        );
    }

}
