package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QLedgerEntryJpaEntity.ledgerEntryJpaEntity;
import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer.CounterpartyKind;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer.Direction;
import com.shinhan.corebank.transfer.application.port.out.CounterpartyTransferQueryPort;
import com.shinhan.corebank.transfer.domain.CorrectionType;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CounterpartyTransferQueryPersistenceAdapter implements CounterpartyTransferQueryPort {

    private final JPAQueryFactory queryFactory;

    // 원장을 계좌와 시각 인덱스(ix_le_account_time 또는 ix_le_account_dir)로 범위 스캔하고 이체를 PK로 붙인다.
    // transfer 만으로 읽으면 입금 쪽(deposit_account_id, 시각) 인덱스가 없어 새로 달아야 한다.
    @Override
    public List<CounterpartyTransfer> findByAccount(
            long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return queryFactory
                .select(
                        transferJpaEntity.transactionNumber,
                        ledgerEntryJpaEntity.direction,
                        transferJpaEntity.withdrawalAccountId,
                        transferJpaEntity.depositAccountId,
                        transferJpaEntity.payeeBankCode,
                        ledgerEntryJpaEntity.amount,
                        ledgerEntryJpaEntity.occurredAt,
                        transferJpaEntity.status)
                .from(ledgerEntryJpaEntity)
                .join(transferJpaEntity)
                .on(transferJpaEntity.transferId.eq(ledgerEntryJpaEntity.transferId))
                .where(
                        ledgerEntryJpaEntity.accountId.eq(accountId),
                        ledgerEntryJpaEntity.occurredAt.goe(fromInclusive),
                        ledgerEntryJpaEntity.occurredAt.lt(toExclusive),
                        // 성공과 처리 불명 이체. 처리 불명 타행 출금은 원장에서 돈이 이미 빠져 미확정으로 담는다.
                        // 무효화된 원거래와 반대 방향인 취소정정은 뺀다
                        transferJpaEntity.status.in(ProcessResultStatus.SUCCESS, ProcessResultStatus.TIMEOUT),
                        transferJpaEntity.invalidatedAt.isNull(),
                        transferJpaEntity
                                .correctionType
                                .isNull()
                                .or(transferJpaEntity.correctionType.ne(CorrectionType.REVERSAL)))
                .orderBy(ledgerEntryJpaEntity.occurredAt.asc(), ledgerEntryJpaEntity.ledgerEntryId.asc())
                .fetch()
                .stream()
                .map(CounterpartyTransferQueryPersistenceAdapter::toCounterpartyTransfer)
                .toList();
    }

    private static CounterpartyTransfer toCounterpartyTransfer(Tuple row) {
        boolean incoming = row.get(ledgerEntryJpaEntity.direction) == LedgerDirection.DEPOSIT;
        boolean interbank = !incoming && row.get(transferJpaEntity.payeeBankCode) != null;

        // 입금이면 상대는 출금계좌, 출금이면 입금계좌다. 타행 출금은 당행 상대 계좌가 없다
        Long counterpartyAccountId =
                incoming ? row.get(transferJpaEntity.withdrawalAccountId) : row.get(transferJpaEntity.depositAccountId);

        return new CounterpartyTransfer(
                row.get(transferJpaEntity.transactionNumber),
                incoming ? Direction.IN : Direction.OUT,
                interbank ? CounterpartyKind.INTERBANK : CounterpartyKind.INTERNAL,
                interbank ? null : counterpartyAccountId,
                row.get(ledgerEntryJpaEntity.amount),
                row.get(ledgerEntryJpaEntity.occurredAt),
                row.get(transferJpaEntity.status) == ProcessResultStatus.SUCCESS);
    }
}
