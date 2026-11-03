package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * @param count              성공 이체 건수
 * @param totalAmount        금액 합계(원)
 * @param firstTransferredAt 가장 이른 처리 시각. 건수가 0이면 null
 */
public record RecentTransferAggregate(int count, long totalAmount, LocalDateTime firstTransferredAt) {

    public static RecentTransferAggregate empty() {
        return new RecentTransferAggregate(0, 0L, null);
    }
}
