package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record PayeeResponse(
        @Schema(description = "입금계좌 ID", example = "2002")
        Long accountId,
        @Schema(description = "예금주명", example = "홍길동")
        String payeeName
) {
    public static PayeeResponse from(PayeeInquiryResult result) {
        return new PayeeResponse(result.accountId(), result.payeeName());
    }
}
