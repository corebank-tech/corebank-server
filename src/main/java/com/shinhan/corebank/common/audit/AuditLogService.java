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

    // success=false(실패 로그): "시도했다"는 사실 자체가 중요해서 호출자 트랜잭션이 롤백되어도 남아야 한다
    // → 즉시 별도 트랜잭션(REQUIRES_NEW)에 커밋.
    // success=true(성공 로그): 호출자 트랜잭션이 "진짜로" 커밋된 뒤에만 남아야 한다
    // → 이벤트로 발행해 AuditLogEventListener가 AFTER_COMMIT 시점에 저장한다.
    // 그 저장이 실패해 (리스너가 예외를 잡아서 로그만 남기므로) 이미 커밋된 업무 트랜잭션에는 영향이 없다
    // — 감사 로그 버그가 실제 업무(예: 자동이체 등록)를 막는 일은 없어야 하기 때문.
    public void record(Long customerId, String transactionNumber, AuditEventType eventType,
                       String requestIp, boolean success, Map<String, Object> detail) {
        LocalDateTime occurredAt = LocalDateTime.now();
        if (!success) {
            auditLogWriter.save(customerId, transactionNumber, eventType, requestIp, false, detail, occurredAt);
            return;
        }
        eventPublisher.publishEvent(new AuditLogEvent(customerId, transactionNumber, eventType,
                requestIp, true, detail, occurredAt));
    }
}
