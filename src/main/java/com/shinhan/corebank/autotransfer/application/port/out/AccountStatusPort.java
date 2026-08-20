package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.account.domain.AccountType;

import java.util.Optional;

// 정상계좌인지, 입금받는 계좌가 있는지 확인하는 인터페이스
public interface AccountStatusPort {
    boolean isActiveAccount(Long accountId);
    Optional<AccountType> findAccountTypeByNumber(String accountNumber);
    boolean belongsToCustomer(Long accountId, Long customerId);

    // 별칭 미설정 계좌는 Optional.empty() - scheduledtransfer의 findAccountAliasesByIds()와 동일하게 alias는 nullable
    Optional<String> findAccountAlias(Long accountId);
}
