package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;

@Builder
public record AutoTransferCancelCommand (Long customerId, String accountPasswordAuthToken, String requestIp) {
    public AutoTransferCancelCommand {
        if (customerId == null || accountPasswordAuthToken == null || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        //공백 문자열 검증
        if (accountPasswordAuthToken.isBlank() || requestIp.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
