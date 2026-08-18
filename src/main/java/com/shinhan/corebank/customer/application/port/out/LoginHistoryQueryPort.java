package com.shinhan.corebank.customer.application.port.out;

import java.util.Optional;

public interface LoginHistoryQueryPort {
    Optional<PreviousLoginRecord> findPreviousSuccessfulLogin(Long customerId);
}
