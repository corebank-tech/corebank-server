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

    // 감사 로그는 실패해도 남아야 하므로 호출한 쪽 트랜잭션이 롤백 되어도 로그는 따로 저장되게 새 트랜잭션 사용
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long customerId, String transactionNumber, AuditEventType eventType,
                       String requestIp, boolean success, Map<String, Object> detail) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.of(customerId, transactionNumber, eventType, requestIp,
                success, detail, LocalDateTime.now());
        auditLogJpaRepository.save(entity);
    }
}
