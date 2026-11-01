package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record DelayedTransferSettingRequest(
        @Schema(description = "지연이체 사용 여부", example = "true") Boolean enabled,
        @Schema(description = "기준금액(원). 이 금액 이상인 당행 즉시이체를 지연한다. 1원 이상", example = "1000000", minimum = "1")
                Long thresholdAmount,
        @Schema(description = "지연시간(시간). 3~24", example = "3", minimum = "3", maximum = "24") Integer delayHours) {

    public DelayedTransferSettingCommand toCommand(Long customerId, String accountPasswordAuthToken) {
        if (enabled == null || thresholdAmount == null || delayHours == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        return new DelayedTransferSettingCommand(
                customerId, enabled, thresholdAmount, delayHours, accountPasswordAuthToken);
    }
}
