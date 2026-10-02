package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QLedgerEntryJpaEntity.ledgerEntryJpaEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.transfer.application.port.out.LedgerBalanceQueryPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerBalanceQueryPort.LedgerBalancePoint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerBalanceQueryPersistenceAdapter implements LedgerBalanceQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<LedgerBalancePoint> findLatestBefore(Long accountId, LocalDateTime beforeExclusive) {

        LedgerEntryJpaEntity entity = queryFactory
                .selectFrom(ledgerEntryJpaEntity)
                .where(
                        ledgerEntryJpaEntity.accountId.eq(accountId),
                        ledgerEntryJpaEntity.occurredAt.lt(beforeExclusive))
                .orderBy(ledgerEntryJpaEntity.occurredAt.desc(), ledgerEntryJpaEntity.ledgerEntryId.desc())
                .fetchFirst();

        return Optional.ofNullable(entity).map(this::toPoint);
    }

    @Override
    public List<LedgerBalancePoint> findBetween(
            Long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {

        return queryFactory
                .selectFrom(ledgerEntryJpaEntity)
                .where(
                        ledgerEntryJpaEntity.accountId.eq(accountId),
                        ledgerEntryJpaEntity.occurredAt.goe(fromInclusive),
                        ledgerEntryJpaEntity.occurredAt.lt(toExclusive))
                .orderBy(ledgerEntryJpaEntity.occurredAt.asc(), ledgerEntryJpaEntity.ledgerEntryId.asc())
                .fetch()
                .stream()
                .map(this::toPoint)
                .toList();
    }

    private LedgerBalancePoint toPoint(LedgerEntryJpaEntity entity) {

        return new LedgerBalancePoint(entity.getOccurredAt(), entity.getLedgerEntryId(), entity.getBalanceAfter());
    }
}
