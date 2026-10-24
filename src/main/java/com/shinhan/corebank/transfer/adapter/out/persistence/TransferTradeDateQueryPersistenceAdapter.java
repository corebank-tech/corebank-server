package com.shinhan.corebank.transfer.adapter.out.persistence;

import static com.shinhan.corebank.transfer.adapter.out.persistence.QTransferJpaEntity.transferJpaEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.application.port.out.TransferTradeDateQueryPort;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransferTradeDateQueryPersistenceAdapter implements TransferTradeDateQueryPort {

    private final JPAQueryFactory queryFactory;

    // 세 조건이 uk_transfer_source_execution_date 컬럼 순서라 인덱스 범위 스캔으로 끝난다.
    @Override
    public Map<SourceExecutionKey, LocalDate> findTradeDates(
            TransferSourceType sourceType,
            Collection<Long> sourceIds,
            LocalDate executionFromInclusive,
            LocalDate executionToInclusive) {
        return queryFactory
                .select(transferJpaEntity.sourceId, transferJpaEntity.executionDate, transferJpaEntity.tradeDate)
                .from(transferJpaEntity)
                .where(
                        transferJpaEntity.sourceType.eq(sourceType),
                        transferJpaEntity.sourceId.in(sourceIds),
                        transferJpaEntity.executionDate.between(executionFromInclusive, executionToInclusive),
                        transferJpaEntity.tradeDate.isNotNull())
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        row -> new SourceExecutionKey(
                                row.get(transferJpaEntity.sourceId), row.get(transferJpaEntity.executionDate)),
                        row -> row.get(transferJpaEntity.tradeDate)));
    }
}
