package com.shinhan.corebank.common.audit;

import java.time.LocalDateTime;
import java.util.Map;

// 성공 감사 로그는 호출자 트랜잭션이 실제로 커밋된 뒤에만 기록해야 하므로,
// 즉시 저장하지 않고 이벤트로 발행해 AuditLogEventListener가 AFTER_COMMIT 시점에 처리하게 한다.
record AuditLogEvent(Long customerId, String transactionNumber, AuditEventType eventType,
                      String requestIp, boolean success, Map<String, Object> detail, LocalDateTime occurredAt) {
}
