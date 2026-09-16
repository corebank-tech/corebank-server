package com.shinhan.corebank.signup.domain.model;

import java.time.LocalDate;

// 기존 은행 원장에서 로컬 등록에 필요한 계좌정보를 전달한다.
public record ExistingBankAccountSnapshot(
        String existingBankAccountId,
        String accountNumber,
        String accountType,
        Long productId,
        long balance,
        String status,
        String passwordHash,
        LocalDate openedDate,
        LocalDate maturityDate) {}
