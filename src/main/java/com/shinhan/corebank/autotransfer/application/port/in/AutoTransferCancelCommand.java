package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;

@Builder
public record AutoTransferCancelCommand (Long customerId, String authToken, String requestIp) {
    public AutoTransferCancelCommand {
        if (customerId == null || authToken == null || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
