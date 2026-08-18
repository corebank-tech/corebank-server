package com.shinhan.corebank.customer.adapter.out.audit;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.shinhan.corebank.common.audit.QAuditLogJpaEntity.auditLogJpaEntity;

@Component
public class LoginHistoryQueryAdapter implements LoginHistoryQueryPort {
    private final JPAQueryFactory queryFactory;

    public LoginHistoryQueryAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Optional<LocalDateTime> findPreviousSuccessfulLogin(Long customerId) {
        LocalDateTime previous = queryFactory
                .select(auditLogJpaEntity.requestedAt)
                .from(auditLogJpaEntity)
                .where(auditLogJpaEntity.customerId.eq(customerId), auditLogJpaEntity.eventType.eq(AuditEventType.LOGIN.name()), auditLogJpaEntity.result.eq("SUCCESS"))
                .orderBy(auditLogJpaEntity.requestedAt.desc(), auditLogJpaEntity.auditLogId.desc())
                .offset(1)
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(previous);
    }
}
