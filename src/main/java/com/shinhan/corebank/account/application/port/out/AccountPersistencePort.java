package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;

public interface AccountPersistencePort {

    Account save(Account account);
}