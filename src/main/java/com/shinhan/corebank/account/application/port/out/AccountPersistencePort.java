package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;

import java.util.List;

public interface AccountPersistencePort {

    Account save(Account account);

    List<Account> findAllByCustomerId(Long customerId);
}