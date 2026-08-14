package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

public record DemandDepositAccountOpeningCommand(
        Long customerId,
        String newAccountPasswordHash
) {
    public DemandDepositAccountOpeningCommand {
        if (customerId == null
                || newAccountPasswordHash == null) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        if (customerId <= 0) {
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
