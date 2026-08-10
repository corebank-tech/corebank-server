package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

// 실제 INSERT를 담당하는 컴포넌트. AuditLogService/AuditLogEventListener가 별도 빈으로 호출해야
// REQUIRES_NEW가 실제로 걸린다(같은 클래스 안에서 자기 자신을 호출하면 프록시를 안 거쳐 무시됨).
@Component
@RequiredArgsConstructor
class AuditLogWriter {
    private final AuditLogJpaRepository auditLogJpaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void save(Long customerId, String transactionNumber, AuditEventType eventType,
              String requestIp, boolean success, Map<String, Object> detail, LocalDateTime occurredAt) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.of(customerId, transactionNumber, eventType, requestIp,
                success, detail, occurredAt);
        auditLogJpaRepository.save(entity);
    }
}
