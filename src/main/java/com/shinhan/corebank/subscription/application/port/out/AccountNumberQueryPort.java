package com.shinhan.corebank.subscription.application.port.out;

import java.util.Optional;

public interface AccountNumberQueryPort {
    Optional<String> findAccountNumberById(Long accountId);
}
