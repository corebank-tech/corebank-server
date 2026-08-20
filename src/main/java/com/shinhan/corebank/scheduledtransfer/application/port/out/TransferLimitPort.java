package com.shinhan.corebank.scheduledtransfer.application.port.out;

// 1회 한도
public interface TransferLimitPort {
    long findOneTimeLimit(Long customerId);
}
