package com.shinhan.corebank.common.audit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditLogService {
    private final AuditLogJpaRepository auditLogJpaRepository;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AuditLogService(
            AuditLogJpaRepository auditLogJpaRepository, PlatformTransactionManager transactionManager, Clock clock) {
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(
            Long customerId,
            String transactionNumber,
            AuditEventType eventType,
            String requestIp,
            boolean success,
            Map<String, Object> detail) {
        save(
                AuditLogJpaEntity.of(
                        customerId, transactionNumber, eventType, requestIp, success, detail, LocalDateTime.now(clock)),
                success);
    }

    // 관리자 고객 개인정보 조회 · 변경 감사(PH-89). 사유(Why)를 access_reason에 남긴다
    public void recordPersonalDataAccess(
            Long adminCustomerId,
            PersonalDataAccessEventType eventType,
            String requestIp,
            boolean success,
            Map<String, Object> detail,
            AccessReason accessReason) {
        save(
                AuditLogJpaEntity.personalDataAccess(
                        adminCustomerId, eventType, requestIp, success, detail, accessReason, LocalDateTime.now(clock)),
                success);
    }

    // 성공 -> 호출자의 트랜잭션에 그대로 합류
    // 저장이 실패하면 호출자의 트랜잭션 전체가 롤백 - 감사 기록을 못 남기면 업무 자체도 성사된 걸로 치지 않는다.
    // 실패 -> 즉시 별도 트랜잭션에 커밋
    private void save(AuditLogJpaEntity entity, boolean success) {
        if (!success) {
            requiresNewTransactionTemplate.executeWithoutResult(status -> auditLogJpaRepository.save(entity));
            return;
        }
        auditLogJpaRepository.save(entity);
    }
}
