package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountType;

import java.time.LocalDate;

public record ProductAccountOpeningCommand(
        Long customerId,
        Long productId,
        AccountType accountType,
        String newAccountPasswordHash,
        LocalDate maturityDate
) {
    public ProductAccountOpeningCommand {
        if (customerId == null
                || productId == null
                || accountType == null
                || newAccountPasswordHash == null
                || maturityDate == null) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        if (customerId <= 0 || productId <= 0) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        if (newAccountPasswordHash.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }
    }
}