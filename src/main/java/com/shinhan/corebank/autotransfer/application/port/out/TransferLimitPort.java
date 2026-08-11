package com.shinhan.corebank.autotransfer.application.port.out;

// 1회 한도 얼마인지
public interface TransferLimitPort {
    long findOneTimeLimit(Long customerId);
}
