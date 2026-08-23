package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountPersistencePort {

    Account save(Account account);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findAllByCustomerId(Long customerId);

    Optional<Account> findByAccountIdAndCustomerId(
            Long accountId,
            Long customerId
    );

    Optional<Account> findByAccountIdAndCustomerIdForUpdate(
            Long accountId,
            Long customerId
    );

    Account updatePasswordState(Account account);
}
