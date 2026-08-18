package com.shinhan.corebank.customer.adapter.out.account;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.customer.application.port.out.AccountLastTransactionQueryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.shinhan.corebank.account.adapter.out.persistence.QAccountJpaEntity.accountJpaEntity;

@Component
public class AccountLastTransactionQueryAdapter implements AccountLastTransactionQueryPort {
    private final JPAQueryFactory queryFactory;

    public AccountLastTransactionQueryAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Optional<LocalDateTime> findLatestTransactionAt(Long customerId) {
        LocalDateTime latest = queryFactory.select(accountJpaEntity.lastTransactionAt.max())
                .from(accountJpaEntity)
                .where(accountJpaEntity.customerId.eq(customerId))
                .fetchOne();
        return Optional.ofNullable(latest);
    }
}
