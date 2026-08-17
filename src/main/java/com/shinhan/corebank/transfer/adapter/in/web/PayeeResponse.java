package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryResult;

public record PayeeResponse(Long accountId, String payeeName) {
    public static PayeeResponse from(PayeeInquiryResult result) {
        return new PayeeResponse(result.accountId(), result.payeeName());
    }
}
