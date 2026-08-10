package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 성공 감사 로그는 호출자 트랜잭션이 진짜로 커밋된 뒤에만 저장한다 —
// 저장(예: 자동이체 등록)이 뒤늦게 실패해서 롤백돼도 "성공" 로그만 남는 것을 막기 위함.
@Component
@RequiredArgsConstructor
class AuditLogEventListener {
    private final AuditLogWriter auditLogWriter;

    // fallbackExecution=true: 활성 트랜잭션 없이 publishEvent()가 호출되면(예: 트랜잭션 밖에서 직접 호출)
    // 기본 동작은 이벤트를 조용히 버리는 것이라, 즉시 실행되도록 방어한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onCommit(AuditLogEvent event) {
        auditLogWriter.save(event.customerId(), event.transactionNumber(), event.eventType(),
                event.requestIp(), event.success(), event.detail(), event.occurredAt());
    }
}
