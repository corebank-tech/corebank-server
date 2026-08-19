package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountPersistencePort {

    Account save(Account account);

    List<Account> findAllByCustomerId(Long customerId);

    Optional<Account> findByAccountIdAndCustomerId(
            Long accountId,
            Long customerId
    );
}