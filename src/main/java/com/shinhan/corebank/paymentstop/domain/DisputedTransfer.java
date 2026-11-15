package com.shinhan.corebank.paymentstop.domain;

import java.time.LocalDateTime;

// 피해 신고된 입금 거래. 등록 시점 원장 값을 남긴다.
public record DisputedTransfer(String transactionNumber, long amount, LocalDateTime occurredAt) {

    public DisputedTransfer {
        if (transactionNumber == null || transactionNumber.isBlank() || occurredAt == null || amount <= 0) {
            throw new IllegalArgumentException("다툼 거래는 거래번호, 양수 금액, 시각이 필요합니다.");
        }
    }
}
