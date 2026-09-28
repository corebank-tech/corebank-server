package com.shinhan.corebank.customer.api;

import java.time.LocalDateTime;

public record ResetCustomerPasswordCommand(
        Long customerId, String expectedPasswordHash, String newPasswordHash, LocalDateTime changedAt) {}
