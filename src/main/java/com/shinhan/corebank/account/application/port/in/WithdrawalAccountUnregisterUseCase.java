package com.shinhan.corebank.account.application.port.in;

public interface WithdrawalAccountUnregisterUseCase {

    WithdrawalAccountUnregisterResult unregister(WithdrawalAccountUnregisterCommand command);
}
