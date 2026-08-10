package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;

    // success=false(실패 로그)는 "시도했다"는 사실 자체가 중요해서, 호출한 쪽 트랜잭션이 롤백되어도
    // 남아야 한다 — 즉시 별도 트랜잭션(REQUIRES_NEW)에 커밋한다.
    // success=true(성공 로그)는 반대로, 호출한 쪽 트랜잭션이 "진짜로" 커밋된 뒤에만 남아야 한다 —
    // 그렇지 않으면 본 작업(예: 자동이체 저장)이 뒤늦게 롤백돼도 성공 로그만 남는 모순이 생긴다.
    // 그래서 성공 로그는 즉시 저장하지 않고 이벤트로 발행해 AuditLogEventListener가 AFTER_COMMIT 시점에 저장한다.
    public void record(Long customerId, String transactionNumber, AuditEventType eventType,
                       String requestIp, boolean success, Map<String, Object> detail) {
        LocalDateTime occurredAt = LocalDateTime.now();
        if (success) {
            eventPublisher.publishEvent(new AuditLogEvent(customerId, transactionNumber, eventType,
                    requestIp, true, detail, occurredAt));
            return;
        }
        auditLogWriter.save(customerId, transactionNumber, eventType, requestIp, false, detail, occurredAt);
    }
}
