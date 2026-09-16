package com.shinhan.corebank.account.application.port.in;

public record WithdrawableAccountResult(Long accountId, String accountNumber, long balance) {}
