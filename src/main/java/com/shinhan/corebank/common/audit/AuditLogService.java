package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogJpaRepository auditLogJpaRepository;

    // @Transactional 하면 안됨 -> 실패 기록이 안남음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long customerId, String transactionNumber, AuditEventType eventType,
                       String requestIp, boolean success, Map<String, Object> detail) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.of(customerId, transactionNumber, eventType, requestIp,
                success, detail, LocalDateTime.now());
        auditLogJpaRepository.save(entity);
    }
}
