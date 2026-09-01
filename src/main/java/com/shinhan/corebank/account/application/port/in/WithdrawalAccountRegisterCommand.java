package com.shinhan.corebank.account.application.port.in;

public record WithdrawalAccountRegisterCommand(
        Long customerId, Long accountId, String accountPasswordAuthToken, String otpAuthToken) {}
