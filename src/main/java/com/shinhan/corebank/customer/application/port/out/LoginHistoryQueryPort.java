package com.shinhan.corebank.customer.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginHistoryQueryPort {
    Optional<LocalDateTime> findPreviousSuccessfulLogin(Long customerId);
}
