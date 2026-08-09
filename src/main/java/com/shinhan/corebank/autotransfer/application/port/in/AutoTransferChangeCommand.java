package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record AutoTransferChangeCommand(Long amount, Integer cycleMonths, LocalDate endDate,
                                        String myPassbookMemo, String recipientPassbookMemo, String authToken, String requestIp) {
    public AutoTransferChangeCommand {
        if (amount == null || cycleMonths == null || endDate == null || authToken == null || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
