package com.shinhan.corebank.customer.adapter.out.account;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.customer.application.port.out.AccountLastTransactionQueryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.shinhan.corebank.account.adapter.out.persistence.QAccountJpaEntity.accountJpaEntity;
import static com.shinhan.corebank.transfer.adapter.out.persistence.QLedgerEntryJpaEntity.ledgerEntryJpaEntity;

@Component
public class AccountLastTransactionQueryAdapter implements AccountLastTransactionQueryPort {
    private final JPAQueryFactory queryFactory;

    public AccountLastTransactionQueryAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


    // account.last_transaction_at 코드 경로 없어 ledger_entry.occured_at 최댓값 사용
    @Override
    public Optional<LocalDateTime> findLatestTransactionAt(Long customerId) {
        LocalDateTime latest = queryFactory.select(ledgerEntryJpaEntity.occurredAt.max())
                .from(ledgerEntryJpaEntity)
                .where(ledgerEntryJpaEntity.accountId.in(JPAExpressions.select(accountJpaEntity.accountId)
                        .from(accountJpaEntity)
                        .where(accountJpaEntity.customerId.eq(customerId))))
                .fetchOne();
        return Optional.ofNullable(latest);
    }
}
