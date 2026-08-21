package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryQueryPort;
import com.shinhan.corebank.transfer.domain.Transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

@Repository
public class TransferHistoryQueryPersistenceAdapter implements TransferHistoryQueryPort {

    private final JPAQueryFactory queryFactory;

    public TransferHistoryQueryPersistenceAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Transfer> search(Long withdrawalAccountId, ProcessResultStatus status, LocalDate fromDate, LocalDate toDate,
                                 TransferHistorySort sort, Pageable pageable) {
        Predicate[] conditions = conditions(withdrawalAccountId, status, fromDate, toDate);
        List<TransferJpaEntity> content = queryFactory
                .selectFrom(transferJpaEntity)
                .where(conditions)
                .orderBy(sortOrder(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory
                .select(transferJpaEntity.count())
                .from(transferJpaEntity)
                .where(conditions)
                .fetchOne();
        List<Transfer> domainContent = content.stream().map(TransferMapper::toDomain).toList();
        return new PageImpl<>(domainContent, pageable, total == null ? 0 : total);
    }

    @Override
    public TransferHistoryAggregate summarize(Long withdrawalAccountId, ProcessResultStatus status, LocalDate fromDate, LocalDate toDate) {
        Predicate[] conditions = conditions(withdrawalAccountId, status, fromDate, toDate);
        List<Tuple> rows = queryFactory
                .select(transferJpaEntity.status, transferJpaEntity.count(), transferJpaEntity.amount.sumLong())
                .from(transferJpaEntity)
                .where(conditions)
                .groupBy(transferJpaEntity.status)
                .fetch();

        long successCount = 0L, successAmount = 0L, errorCount = 0L, errorAmount = 0L;
        for (Tuple row : rows) {
            ProcessResultStatus rowStatus = row.get(transferJpaEntity.status);
            long count = row.get(transferJpaEntity.count());
            Long amountSum = row.get(transferJpaEntity.amount.sumLong());
            long amount = amountSum == null ? 0L : amountSum;
            if (rowStatus == ProcessResultStatus.SUCCESS) {
                successCount = count;
                successAmount = amount;
            } else if (rowStatus == ProcessResultStatus.ERROR) {
                errorCount = count;
                errorAmount = amount;
            }
        }
        return new TransferHistoryAggregate(successCount, successAmount, errorCount, errorAmount);
    }

    // toDate는 포함(inclusive)이라 다음날 자정 미만으로 반개구간을 만든다(LedgerHistoryQueryPersistenceAdapter와 동일 관행)
    private Predicate[] conditions(Long withdrawalAccountId, ProcessResultStatus status, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime toExclusive = toDate.plusDays(1).atStartOfDay();

        return new Predicate[] {
                transferJpaEntity.withdrawalAccountId.eq(withdrawalAccountId),
                transferJpaEntity.transferredAt.goe(from),
                transferJpaEntity.transferredAt.lt(toExclusive),
                statusEq(status)
        };
    }

    private BooleanExpression statusEq(ProcessResultStatus status) {
        return status != null ? transferJpaEntity.status.eq(status) : null;
    }

    private OrderSpecifier<?>[] sortOrder(TransferHistorySort sort) {
        if (sort == TransferHistorySort.OLDEST) {
            return new OrderSpecifier<?>[] { transferJpaEntity.transferredAt.asc(), transferJpaEntity.transferId.asc() };
        }
        return new OrderSpecifier<?>[] { transferJpaEntity.transferredAt.desc(), transferJpaEntity.transferId.desc() };
    }
}
