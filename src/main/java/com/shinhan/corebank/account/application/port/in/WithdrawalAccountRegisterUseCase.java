package com.shinhan.corebank.account.application.port.in;

public interface WithdrawalAccountRegisterUseCase {

    WithdrawalAccountRegisterResult register(
            WithdrawalAccountRegisterCommand command
    );
}