package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScheduledTransferCancelRequest(
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                """
                        취소할 예약이체 ID 목록. 최대 50건(중복 제거 후 기준). 서버는 오름차순 정렬·중복 제거한 뒤 처리하므로, \
                        OTP 발급(`POST /otp/issue`) 시 `transactionData.scheduledTransferIds`에도 같은 규칙으로 \
                        정렬·중복 제거한 배열을 담아야 한다. 배열 순서가 다르면 `OTP0102`가 발생한다.""",
                                        requiredMode = Schema.RequiredMode.REQUIRED,
                                        example = "[1, 2, 3]"),
                        minItems = 1,
                        maxItems = 50)
                List<Long> scheduledTransferIds) {}
