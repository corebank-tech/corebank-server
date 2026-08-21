package com.shinhan.corebank.account.application.port.in;

import java.util.Optional;

public interface AccountNumberQueryUseCase {

    Optional<String> findAccountNumber(
            Long accountId,
            Long customerId
    );
}
