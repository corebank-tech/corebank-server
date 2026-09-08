package com.shinhan.corebank.auth.adapter.out.customer;

import com.shinhan.corebank.auth.application.port.out.FindIdCustomerCandidate;
import com.shinhan.corebank.auth.application.port.out.FindIdCustomerPort;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

// Auth의 아이디 찾기 고객 조회를 Customer 공개 API로 연결한다.
@Component
@RequiredArgsConstructor
public class FindIdCustomerAdapter implements FindIdCustomerPort {

    private final CustomerAuthenticationFacade customerFacade;

    @Override
    public List<FindIdCustomerCandidate> findAllByIdentity(
            String customerName,
            LocalDate birthDate
    ) {
        return customerFacade.findAllByIdentity(customerName, birthDate)
                .stream()
                .map(customer -> new FindIdCustomerCandidate(
                        customer.customerId(),
                        customer.userId()
                ))
                .toList();
    }
}
