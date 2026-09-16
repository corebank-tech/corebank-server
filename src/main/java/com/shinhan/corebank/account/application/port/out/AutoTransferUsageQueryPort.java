package com.shinhan.corebank.account.application.port.out;

public interface AutoTransferUsageQueryPort {

    boolean existsUsingWithdrawalAccount(Long withdrawalAccountId);
}
