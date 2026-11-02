package com.shinhan.corebank.transfer.api;

/** 보류한 이체를 푸는 방식. */
public enum TransferHoldReleaseMode {
    // 정한 시각이 되면 실행한다(지연이체)
    AT_TIME,

    // 관리자가 승인하면 실행한다(FDS)
    ON_APPROVAL
}
