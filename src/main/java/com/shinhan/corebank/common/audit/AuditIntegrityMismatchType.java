package com.shinhan.corebank.common.audit;

// 사본과 DB를 대조해 나온 불일치 종류(PH-82)
public enum AuditIntegrityMismatchType {
    TAMPERED, // DB 행을 다시 계산한 해시가 사본의 해시와 다르다
    MISSING_IN_DB, // 사본에는 있는데 DB에서 사라졌다
    NOT_ARCHIVED // DB에는 있는데 사본에 없다
}
