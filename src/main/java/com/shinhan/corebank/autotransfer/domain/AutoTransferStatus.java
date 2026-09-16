package com.shinhan.corebank.autotransfer.domain;

public enum AutoTransferStatus {
    NORMAL, // 운용 중 ( 정상 )
    EXPIRED, // 기간 만료로 시스템 자동 종료
    TERMINATED; // 고객 직접 해지

    // normal 일때만 true 반환
    public boolean isModifiable() {
        return this == NORMAL;
    }
}
