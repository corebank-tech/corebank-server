package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.account.domain.AccountType;

import java.util.Optional;

// 정상계좌인지, 입금받는 계좌가 있는지 확인하는 인터페이스
public interface AccountStatusPort {
    boolean isActiveAccount(Long accountId);
    Optional<AccountType> findAccountTypeByNumber(String accountNumber);
    boolean belongsToCustomer(Long accountId, Long customerId);
}
