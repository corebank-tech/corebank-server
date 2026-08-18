package com.shinhan.corebank.transfer.application.port.in;

import lombok.Builder;

@Builder
public record PayeeInquiryResult(Long accountId, String payeeName) {
}
