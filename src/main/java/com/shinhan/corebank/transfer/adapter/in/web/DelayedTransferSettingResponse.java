package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DelayedTransferSettingResponse(
        @Schema(description = "지연이체 사용 여부. 저장한 적 없으면 false", example = "false") boolean enabled,
        @Schema(description = "기준금액(원). 저장한 적 없으면 기본값 1000000", example = "1000000") long thresholdAmount,
        @Schema(description = "지연시간(시간). 저장한 적 없으면 기본값 3", example = "3") int delayHours) {

    public static DelayedTransferSettingResponse from(DelayedTransferSettingResult result) {
        return new DelayedTransferSettingResponse(result.enabled(), result.thresholdAmount(), result.delayHours());
    }
}
