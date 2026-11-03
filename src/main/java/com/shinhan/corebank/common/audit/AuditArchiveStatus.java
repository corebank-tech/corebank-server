package com.shinhan.corebank.common.audit;

public enum AuditArchiveStatus {
    PENDING, // 업로드 전에 커밋한다. 중단되면 다음 적재가 이어서 올린다
    SUCCESS, // 사본 확정. 다시 올리지 않는다
    FAILURE // 마지막 시도가 실패했다. 다음 적재가 다시 시도한다
}
