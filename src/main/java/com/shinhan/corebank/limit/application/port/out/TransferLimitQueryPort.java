package com.shinhan.corebank.limit.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

/** 상태를 바꾸지 않는 한도 조회. */
public interface TransferLimitQueryPort {

    Optional<TransferLimit> findByCustomerId(Long customerId);

    Optional<TransferLimitDailyUsage> findUsage(Long customerId, LocalDate usageDate);
}
