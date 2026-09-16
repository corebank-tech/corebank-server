package com.shinhan.corebank.customer.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 인터넷뱅킹 신규 고객 등록정보를 전달한다.
public record RegisterCustomerCommand(
        String userId,
        String existingBankCustomerId,
        String passwordHash,
        String userName,
        LocalDate birthDate,
        String email,
        String phoneNumber,
        LocalDateTime joinedAt) {}
