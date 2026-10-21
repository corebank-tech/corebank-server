package com.shinhan.corebank.account.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AccountStatusHistoryPort {

    void recordMaturity(Long accountId, LocalDate maturityDate, LocalDate maturityBaseDate, LocalDateTime changedAt);
}
