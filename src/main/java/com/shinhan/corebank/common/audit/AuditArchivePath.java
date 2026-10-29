package com.shinhan.corebank.common.audit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 버킷의 audit/ 아래 하위 경로(PH-72 README가 PH-82에 맡긴 자리). 관리자 개인정보 조회·변경 이벤트는 access 경로로 따로 둔다.
public enum AuditArchivePath {
    LOG("audit/log/"),
    ACCESS("audit/access/");

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final String prefix;

    AuditArchivePath(String prefix) {
        this.prefix = prefix;
    }

    public static AuditArchivePath of(AuditLogJpaEntity auditLog) {
        return AuditEventType.valueOf(auditLog.getEventType()).requiresAccessReason() ? ACCESS : LOG;
    }

    public String keyOf(LocalDate archiveDate) {
        return prefix + archiveDate.format(DATE_PATH) + ".jsonl";
    }
}
