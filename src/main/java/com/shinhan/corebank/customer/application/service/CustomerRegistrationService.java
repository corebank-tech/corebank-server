package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.CustomerRegistration;
import com.shinhan.corebank.customer.api.RegisterCustomerCommand;
import com.shinhan.corebank.customer.api.RegisteredCustomer;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import org.springframework.stereotype.Service;

// 검증된 가입정보로 신규 인터넷뱅킹 고객을 등록한다.
@Service
public class CustomerRegistrationService implements CustomerRegistration {

    private final CustomerPersistencePort customerPersistencePort;

    public CustomerRegistrationService(
            CustomerPersistencePort customerPersistencePort
    ) {
        this.customerPersistencePort = customerPersistencePort;
    }

    @Override
    public RegisteredCustomer register(RegisterCustomerCommand command) {
        Customer saved = customerPersistencePort.save(Customer.register(
                command.userId(), command.existingBankCustomerId(),
                command.passwordHash(), command.userName(),
                command.birthDate(), command.email(), command.phoneNumber(),
                command.joinedAt()
        ));
        return new RegisteredCustomer(saved.getCustomerId(), saved.getUserId());
    }
}
