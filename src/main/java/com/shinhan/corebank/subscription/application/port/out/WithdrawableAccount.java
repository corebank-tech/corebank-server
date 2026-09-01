package com.shinhan.corebank.subscription.application.port.out;

public record WithdrawableAccount(Long accountId, String accountNumber, long balance) {}
