package com.shinhan.corebank.customer.adapter.out.audit;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import com.shinhan.corebank.customer.application.port.out.PreviousLoginRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.shinhan.corebank.common.audit.QAuditLogJpaEntity.auditLogJpaEntity;

@Component
public class LoginHistoryQueryAdapter implements LoginHistoryQueryPort {
    private final JPAQueryFactory queryFactory;

    public LoginHistoryQueryAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Optional<PreviousLoginRecord> findPreviousSuccessfulLogin(Long customerId) {
        PreviousLoginRecord previous = queryFactory
                .select(Projections.constructor(PreviousLoginRecord.class, auditLogJpaEntity.requestedAt, auditLogJpaEntity.requestIp))
                .from(auditLogJpaEntity)
                .where(auditLogJpaEntity.customerId.eq(customerId), auditLogJpaEntity.eventType.eq(AuditEventType.LOGIN.name()), auditLogJpaEntity.result.eq("SUCCESS"))
                .orderBy(auditLogJpaEntity.requestedAt.desc(), auditLogJpaEntity.auditLogId.desc())
                .offset(1)
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(previous);
    }
}
