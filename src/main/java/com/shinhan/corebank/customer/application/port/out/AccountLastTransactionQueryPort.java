package com.shinhan.corebank.customer.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountLastTransactionQueryPort {
    Optional<LocalDateTime> findLatestTransactionAt(Long customerId);
}
