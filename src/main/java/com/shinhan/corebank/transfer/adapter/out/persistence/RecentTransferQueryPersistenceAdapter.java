package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QAccountLockJpaEntity.accountLockJpaEntity;
import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.api.RecentSuccessfulTransfer;
import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferQueryPort;
import com.shinhan.corebank.transfer.application.port.out.SucceededTransferHistoryPort;
import com.shinhan.corebank.transfer.domain.CorrectionType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecentTransferQueryPersistenceAdapter implements RecentTransferQueryPort, SucceededTransferHistoryPort {

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
    public List<RecentSuccessfulTransfer> findSucceeded(
            long customerId, String depositAccountNumber, LocalDateTime since) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(transferJpaEntity.transferId, transferJpaEntity.amount, transferJpaEntity.transferredAt)
                .from(transferJpaEntity)
                .where(succeededTo(accountIds, depositAccountNumber), transferJpaEntity.transferredAt.goe(since))
                .orderBy(transferJpaEntity.transferredAt.asc(), transferJpaEntity.transferId.asc())
                .fetch()
                .stream()
                .map(row -> new RecentSuccessfulTransfer(
                        row.get(transferJpaEntity.transferId),
                        row.get(transferJpaEntity.amount),
                        row.get(transferJpaEntity.transferredAt)))
                .toList();
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

    @Override
    public List<SucceededTransfer> findRecentSucceeded(
            long customerId, LocalDateTime fromInclusive, LocalDateTime toExclusive, int limit) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(
                        transferJpaEntity.transferId,
                        transferJpaEntity.amount,
                        transferJpaEntity.transferredAt,
                        transferJpaEntity.depositAccountNumber)
                .from(transferJpaEntity)
                .where(
                        succeededFrom(accountIds),
                        transferJpaEntity.transferredAt.goe(fromInclusive),
                        transferJpaEntity.transferredAt.lt(toExclusive))
                // 같은 시각끼리 순서가 고정돼야 limit 경계에서 잘리는 건이 매번 같다
                .orderBy(transferJpaEntity.transferredAt.desc(), transferJpaEntity.transferId.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new SucceededTransfer(
                        row.get(transferJpaEntity.transferId).longValue(),
                        row.get(transferJpaEntity.amount).longValue(),
                        row.get(transferJpaEntity.transferredAt),
                        row.get(transferJpaEntity.depositAccountNumber)))
                .toList();
    }

    @Override
    public RecipientHistory summarizeRecipient(
            long customerId, String depositAccountNumber, long excludeWithdrawalAccountId) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return new RecipientHistory(null, 0);
        }
        // 출금계좌마다 한 줄로 묶어 한 번만 읽는다. 고객 계좌 수만큼만 줄이 나온다
        List<Tuple> perAccount = queryFactory
                .select(transferJpaEntity.withdrawalAccountId, transferJpaEntity.transferredAt.min())
                .from(transferJpaEntity)
                .where(succeededTo(accountIds, depositAccountNumber))
                .groupBy(transferJpaEntity.withdrawalAccountId)
                .fetch();
        LocalDateTime first = perAccount.stream()
                .map(row -> row.get(transferJpaEntity.transferredAt.min()))
                .min(Comparator.naturalOrder())
                .orElse(null);
        int others = (int) perAccount.stream()
                .filter(row -> row.get(transferJpaEntity.withdrawalAccountId).longValue() != excludeWithdrawalAccountId)
                .count();
        return new RecipientHistory(first, others);
    }

    // 계좌 목록을 먼저 구해 IN으로 넘긴다. 서브쿼리보다 ix_transfer_wacc(출금계좌, 처리시각) 범위 스캔이 확실하다
    private List<Long> accountIdsOf(long customerId) {
        return queryFactory
                .select(accountLockJpaEntity.accountId)
                .from(accountLockJpaEntity)
                .where(accountLockJpaEntity.customerId.eq(customerId))
                .fetch();
    }

    private BooleanExpression succeededTo(List<Long> accountIds, String depositAccountNumber) {
        return succeededFrom(accountIds).and(transferJpaEntity.depositAccountNumber.eq(depositAccountNumber));
    }

    // 당행 성공 이체만. 무효화된 원거래와 반대 방향인 취소정정은 뺀다
    private BooleanExpression succeededFrom(List<Long> accountIds) {
        return transferJpaEntity
                .withdrawalAccountId
                .in(accountIds)
                .and(transferJpaEntity.payeeBankCode.isNull())
                .and(transferJpaEntity.status.eq(ProcessResultStatus.SUCCESS))
                .and(transferJpaEntity.invalidatedAt.isNull())
                .and(transferJpaEntity
                        .correctionType
                        .isNull()
                        .or(transferJpaEntity.correctionType.ne(CorrectionType.REVERSAL)));
    }
}
