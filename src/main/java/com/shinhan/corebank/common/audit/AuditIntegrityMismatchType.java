package com.shinhan.corebank.common.audit;

// 사본과 DB를 대조해 나온 불일치 종류(PH-82)
public enum AuditIntegrityMismatchType {
    TAMPERED, // 사본과 내용이 달라졌다(변조 의심). DB 행을 다시 계산한 해시가 사본의 해시와 다르다
    MISSING_IN_DB, // 적재한 뒤 DB에서 지워졌다(삭제 의심). 사본에는 있는데 DB에 없다
    NOT_ARCHIVED // 적재가 빠졌거나 사후에 끼워 넣은 행이다. DB에는 있는데 사본에 없다
}
