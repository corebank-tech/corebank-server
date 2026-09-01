package com.shinhan.corebank.scheduledtransfer.domain;

public enum ScheduledTransferStatus {
    WAITING, // 대기
    PROCESSING, // 처리중
    SUCCESS, // 성공
    FAILED, // 오류
    CANCELED; // 고객 취소

    public boolean isCancelable() {
        return this == WAITING;
    }
}
