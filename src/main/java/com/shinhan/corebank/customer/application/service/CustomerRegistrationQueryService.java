package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.CustomerRegistrationQuery;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 고객 저장소를 외부에 노출하지 않고 가입 중복조회 기능만 제공한다.
@Service
@RequiredArgsConstructor
public class CustomerRegistrationQueryService
        implements CustomerRegistrationQuery {

    private final CustomerPersistencePort customerPersistencePort;

    @Override
    public boolean existsByUserId(String userId) {
        return customerPersistencePort.existsByUserId(userId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return customerPersistencePort.existsByEmail(email);
    }

    @Override
    public boolean existsByExistingBankCustomerId(
            String existingBankCustomerId
    ) {
        return customerPersistencePort.existsByExistingBankCustomerId(
                existingBankCustomerId
        );
    }
}
