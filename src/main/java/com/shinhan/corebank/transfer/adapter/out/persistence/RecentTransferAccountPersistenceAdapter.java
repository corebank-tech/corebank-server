package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QAccountLockJpaEntity.accountLockJpaEntity;
import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.out.RecentDepositAccount;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferAccountQueryPort;
import com.shinhan.corebank.transfer.domain.CorrectionType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecentTransferAccountPersistenceAdapter implements RecentTransferAccountQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RecentDepositAccount> findRecentDepositAccounts(long customerId, LocalDateTime since, int limit) {
        List<Long> accountIds = accountIdsOf(customerId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        DateTimeExpression<LocalDateTime> lastTransferredAt = transferJpaEntity.transferredAt.max();
        return queryFactory
                .select(transferJpaEntity.depositAccountNumber, lastTransferredAt)
                .from(transferJpaEntity)
                .where(
                        transferJpaEntity.withdrawalAccountId.in(accountIds),
                        transferJpaEntity.transferredAt.goe(since),
                        transferJpaEntity.depositAccountId.notIn(accountIds),
                        transferJpaEntity.payeeBankCode.isNull(),
                        transferJpaEntity.status.eq(ProcessResultStatus.SUCCESS),
                        transferJpaEntity.invalidatedAt.isNull(),
                        transferJpaEntity
                                .correctionType
                                .isNull()
                                .or(transferJpaEntity.correctionType.ne(CorrectionType.REVERSAL)))
                .groupBy(transferJpaEntity.depositAccountNumber)
                .orderBy(lastTransferredAt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new RecentDepositAccount(
                        row.get(transferJpaEntity.depositAccountNumber), row.get(lastTransferredAt)))
                .toList();
    }

    // 계좌 목록을 먼저 구해 IN으로 넘긴다. ix_transfer_wacc(출금계좌, 처리시각) 범위 스캔을 타게 하려는 것이다
    private List<Long> accountIdsOf(long customerId) {
        return queryFactory
                .select(accountLockJpaEntity.accountId)
                .from(accountLockJpaEntity)
                .where(accountLockJpaEntity.customerId.eq(customerId))
                .fetch();
    }
}
