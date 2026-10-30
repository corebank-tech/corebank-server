package com.shinhan.corebank.common.audit;

import java.time.LocalDateTime;
import java.util.Map;

// 사본 JSONL 한 줄. 감사 행 전 컬럼과 저장 시점 해시를 그대로 담아, 검증 배치가 DB 행과 대조하는 기준이 된다.
public record AuditArchiveLine(
        Long auditLogId,
        Long customerId,
        String transactionNumber,
        String eventType,
        String requestIp,
        String result,
        Map<String, Object> detail,
        String accessReason,
        LocalDateTime requestedAt,
        String rowHash) {

    public static AuditArchiveLine from(AuditLogJpaEntity auditLog) {
        return new AuditArchiveLine(
                auditLog.getAuditLogId(),
                auditLog.getCustomerId(),
                auditLog.getTransactionNumber(),
                auditLog.getEventType(),
                auditLog.getRequestIp(),
                auditLog.getResult(),
                auditLog.getDetail(),
                auditLog.getAccessReason(),
                auditLog.getRequestedAt(),
                auditLog.getRowHash());
    }
}
