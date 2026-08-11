package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 성공 감사 로그는 호출자 트랜잭션이 진짜로 커밋된 뒤에만 저장한다
// — 저장(예: 자동이체 등록)이 뒤늦게 실패해서 롤백돼도 "성공" 로그만 남는 것을 막기 위함.
@Component
@RequiredArgsConstructor
class AuditLogEventListener {
    private static final Logger log = LoggerFactory.getLogger(AuditLogEventListener.class);

    private final AuditLogWriter auditLogWriter;

    // fallbackExecution=true: 활성 트랜잭션 없이 publishEvent()가 호출되면(예: 트랜잭션 밖에서 직접 호출)
    // 기본 동작은 이벤트를 조용히 버리는 것이라, 즉시 실행되도록 방어한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onCommit(AuditLogEvent event) {
        // 이 시점엔 호출자(업무) 트랜잭션이 이미 커밋되어 끝난 상태다. 감사 로그 저장 자체가 실패하더라도 이미 성공한 업무에는 절대 영향을 주면 안 되므로,
        // 예외를 밖으로 던지지 않고 에러 로그만 남긴다. 운영에서는 이 로그를 모니터링해서 감사 로그 유실을 감지·복구해야 한다.
        try {
            auditLogWriter.save(event.customerId(), event.transactionNumber(), event.eventType(),
                    event.requestIp(), event.success(), event.detail(), event.occurredAt());
        } catch (Exception e) {
            log.error("감사 로그 저장 실패 — customerId={}, eventType={}, transactionNumber={}",
                    event.customerId(), event.eventType(), event.transactionNumber(), e);
        }
    }
}
