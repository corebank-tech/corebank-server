package com.shinhan.corebank.account.application.port.in;

public interface AccountAliasUseCase {
    AccountAliasResult changeAlias(Long customerId, Long accountId, String alias);

    void deleteAlias(Long customerId, Long accountId);
}
