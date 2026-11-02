package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QAccountLockJpaEntity.accountLockJpaEntity;
import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferQueryPort;
import com.shinhan.corebank.transfer.domain.CorrectionType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecentTransferQueryPersistenceAdapter implements RecentTransferQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public RecentTransferAggregate aggregateSucceeded(
            long customerId, String depositAccountNumber, LocalDateTime since) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return RecentTransferAggregate.empty();
        }
        Tuple row = queryFactory
                .select(
                        transferJpaEntity.count(),
                        transferJpaEntity.amount.sumLong(),
                        transferJpaEntity.transferredAt.min())
                .from(transferJpaEntity)
                .where(succeededTo(accountIds, depositAccountNumber), transferJpaEntity.transferredAt.goe(since))
                .fetchOne();
        long count = row == null ? 0L : row.get(transferJpaEntity.count());
        if (count == 0L) {
            return RecentTransferAggregate.empty();
        }
        return new RecentTransferAggregate(
                (int) count,
                row.get(transferJpaEntity.amount.sumLong()),
                row.get(transferJpaEntity.transferredAt.min()));
    }

    @Override
    public boolean existsSucceededBefore(long customerId, String depositAccountNumber, LocalDateTime before) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return false;
        }
        Integer found = queryFactory
                .selectOne()
                .from(transferJpaEntity)
                .where(succeededTo(accountIds, depositAccountNumber), transferJpaEntity.transferredAt.lt(before))
                .fetchFirst();
        return found != null;
    }

    // 계좌 목록을 먼저 구해 IN으로 넘긴다. 서브쿼리보다 ix_transfer_wacc(출금계좌, 처리시각) 범위 스캔이 확실하다
    private List<Long> accountIdsOf(long customerId) {
        return queryFactory
                .select(accountLockJpaEntity.accountId)
                .from(accountLockJpaEntity)
                .where(accountLockJpaEntity.customerId.eq(customerId))
                .fetch();
    }

    // 당행 성공 이체만. 무효화된 원거래와 반대 방향인 취소정정은 뺀다
    private BooleanExpression succeededTo(List<Long> accountIds, String depositAccountNumber) {
        return transferJpaEntity
                .withdrawalAccountId
                .in(accountIds)
                .and(transferJpaEntity.depositAccountNumber.eq(depositAccountNumber))
                .and(transferJpaEntity.payeeBankCode.isNull())
                .and(transferJpaEntity.status.eq(ProcessResultStatus.SUCCESS))
                .and(transferJpaEntity.invalidatedAt.isNull())
                .and(transferJpaEntity
                        .correctionType
                        .isNull()
                        .or(transferJpaEntity.correctionType.ne(CorrectionType.REVERSAL)));
    }
}
