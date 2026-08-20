package com.shinhan.corebank.account.application.port.out;

public interface ScheduledTransferUsageQueryPort {

    boolean existsUsingWithdrawalAccount(
            Long withdrawalAccountId
    );
}
