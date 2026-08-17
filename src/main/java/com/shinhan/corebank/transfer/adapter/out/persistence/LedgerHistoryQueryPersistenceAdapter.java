package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryQueryPort;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerEntry;

import org.springframework.stereotype.Repository;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QLedgerEntryJpaEntity.ledgerEntryJpaEntity;

@Repository
public class LedgerHistoryQueryPersistenceAdapter implements LedgerHistoryQueryPort {

    private final JPAQueryFactory queryFactory;

    public LedgerHistoryQueryPersistenceAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<LedgerEntry> findEntries(LedgerHistoryQuery query) {
        return queryFactory
                .selectFrom(ledgerEntryJpaEntity)
                .where(conditions(query))
                .orderBy(sortOrder(query.sort()))
                // query.page()는 0-based (LedgerHistoryQuery 계약: P2가 외부 1-based를 변환해 전달)
                .offset((long) query.page() * query.size())
                .limit(query.size())
                .fetch()
                .stream()
                .map(LedgerEntryMapper::toDomain)
                .toList();
    }

    @Override
    public long countEntries(LedgerHistoryQuery query) {
        Long count = queryFactory
                .select(ledgerEntryJpaEntity.count())
                .from(ledgerEntryJpaEntity)
                .where(conditions(query))
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public LedgerHistoryAggregate summarize(LedgerHistoryQuery query) {
        List<Tuple> rows = queryFactory
                .select(ledgerEntryJpaEntity.direction, ledgerEntryJpaEntity.count(), ledgerEntryJpaEntity.amount.sumLong())
                .from(ledgerEntryJpaEntity)
                .where(conditions(query))
                .where(excludeReversed())
                .groupBy(ledgerEntryJpaEntity.direction)
                .fetch();

        long depositCount = 0L;
        long depositAmount = 0L;
        long withdrawalCount = 0L;
        long withdrawalAmount = 0L;

        for (Tuple row : rows) {
            LedgerDirection direction = row.get(ledgerEntryJpaEntity.direction);
            long count = row.get(ledgerEntryJpaEntity.count());
            Long amountSum = row.get(ledgerEntryJpaEntity.amount.sumLong());
            long amount = amountSum == null ? 0L : amountSum;

            if (direction == LedgerDirection.DEPOSIT) {
                depositCount = count;
                depositAmount = amount;
            } else if (direction == LedgerDirection.WITHDRAWAL) {
                withdrawalCount = count;
                withdrawalAmount = amount;
            }
        }

        return new LedgerHistoryAggregate(depositCount, depositAmount, withdrawalCount, withdrawalAmount);
    }

    private Predicate[] conditions(LedgerHistoryQuery query) {
        // toDate는 포함(inclusive)이라 다음날 자정 미만으로 반개구간을 만든다.
        LocalDateTime from = query.fromDate().atStartOfDay();
        LocalDateTime toExclusive = query.toDate().plusDays(1).atStartOfDay();

        return new Predicate[] {
                ledgerEntryJpaEntity.accountId.eq(query.accountId()),
                ledgerEntryJpaEntity.occurredAt.goe(from),
                ledgerEntryJpaEntity.occurredAt.lt(toExclusive),
                directionEq(query.direction()),
                keywordContains(query.keyword())
        };
    }

    private BooleanExpression directionEq(LedgerHistoryDirection direction) {
        if (direction == null || direction == LedgerHistoryDirection.ALL) {
            return null;
        }
        return ledgerEntryJpaEntity.direction.eq(LedgerDirection.valueOf(direction.name()));
    }

    private BooleanExpression excludeReversed() {
        // 취소된 원거래(reversed=true)와 그 반대기표(REVERSAL, reversalId != null)는
        // 목록에는 그대로 노출하되(정책 A) 무효화된 금액이므로 집계에서는 제외한다.
        return ledgerEntryJpaEntity.reversed.isFalse().and(ledgerEntryJpaEntity.reversalId.isNull());
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return ledgerEntryJpaEntity.transactionContent.contains(keyword);
    }

    private OrderSpecifier<?>[] sortOrder(LedgerHistorySort sort) {
        if (sort == LedgerHistorySort.OLDEST) {
            return new OrderSpecifier<?>[] { ledgerEntryJpaEntity.occurredAt.asc(), ledgerEntryJpaEntity.ledgerEntryId.asc() };
        }
        return new OrderSpecifier<?>[] { ledgerEntryJpaEntity.occurredAt.desc(), ledgerEntryJpaEntity.ledgerEntryId.desc() };
    }
}
