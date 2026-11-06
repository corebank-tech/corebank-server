package com.shinhan.corebank.paymentstop.adapter.out.account;

import com.shinhan.corebank.account.api.CustomerAccountNumberQuery;
import com.shinhan.corebank.paymentstop.application.port.out.CustomerAccountPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerAccountAdapter implements CustomerAccountPort {

    private final CustomerAccountNumberQuery customerAccountNumberQuery;

    @Override
    public Map<Long, String> accountNumbersOf(long customerId) {
        return customerAccountNumberQuery.findAccountNumbersByCustomerId(customerId);
    }
}
