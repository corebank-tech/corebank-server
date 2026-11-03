package com.shinhan.corebank.account.application.port.out;

public interface AccountTransferLifecycleLockPort {

    void lockForRegistration(Long customerId, Long withdrawalAccountId, String depositAccountNumber);

    void lockForEarlyTermination(Long customerId, Long productAccountId, Long destinationAccountId);
}
