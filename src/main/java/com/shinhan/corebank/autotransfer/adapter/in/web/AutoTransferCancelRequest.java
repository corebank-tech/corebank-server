package com.shinhan.corebank.autotransfer.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AutoTransferCancelRequest(
        @Schema(description = """
                해지할 자동이체 ID 목록. 최대 50건. 서버는 오름차순 정렬·중복 제거한 뒤 처리하므로, \
                OTP 발급(`POST /otp/issue`) 시 `transactionData.autoTransferIds`에도 같은 규칙으로 \
                정렬·중복 제거한 배열을 담아야 한다. 배열 순서가 다르면 `OTP0102`가 발생한다.""",
                example = "[4, 5]")
        List<Long> autoTransferIds) {
}
