package com.shinhan.corebank.customer.api;

public record PasswordResetCustomerData(
        Long customerId,
        String userId,
        String customerName,
        String email,
        String passwordHash,
        boolean accountLocked) {}
