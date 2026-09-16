package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 이체한도 조회 응답(REQ-TRSF-024). 금액은 모두 원 단위 정수다.
 */
@Builder
public record TransferLimitResponse(
        @Schema(description = "1회 이체한도", example = "1000000") long oneTimeLimit,
        @Schema(description = "1일 이체한도", example = "5000000") long dailyLimit,
        @Schema(description = "당일 사용금액. KST 영업일 기준", example = "300000") long dailyUsedAmount,
        @Schema(description = "당일 잔여 이체가능금액. 음수가 되지 않는다", example = "4700000") long dailyRemainingAmount) {

    public static TransferLimitResponse from(TransferLimitResult result) {
        return TransferLimitResponse.builder()
                .oneTimeLimit(result.oneTimeLimit())
                .dailyLimit(result.dailyLimit())
                .dailyUsedAmount(result.dailyUsedAmount())
                .dailyRemainingAmount(result.dailyRemainingAmount())
                .build();
    }
}
