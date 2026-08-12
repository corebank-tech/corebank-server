package com.shinhan.corebank.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogJpaRepository auditLogJpaRepository;

   // 감사 로그 저장은 호출자의 트랜잭션에 그대로 합류
    //  저장이 실패하면 호출자의 트랜잭션 전체가 롤백
    // 감사 기록을 못 남기면 업무 자체도 성사된 걸로 치지 않는다.
    public void record(Long customerId, String transactionNumber, AuditEventType eventType,
                       String requestIp, boolean success, Map<String, Object> detail) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.of(customerId, transactionNumber, eventType,
                requestIp, success, detail, LocalDateTime.now());
        auditLogJpaRepository.save(entity);
    }
}
