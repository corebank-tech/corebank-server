package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferBatchQueryPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryAggregate;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryQueryPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryRow;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shinhan.corebank.autotransfer.adapter.out.persistence.QAutoTransferExecutionJpaEntity.autoTransferExecutionJpaEntity;
import static com.shinhan.corebank.autotransfer.adapter.out.persistence.QAutoTransferJpaEntity.autoTransferJpaEntity;

@Repository
@RequiredArgsConstructor
// 이 파일은 DB에 직접 접근하게 해주는 파일
public class AutoTransferPersistenceAdapter implements AutoTransferPersistencePort, AutoTransferQueryPort, AutoTransferBatchQueryPort,
        AutoTransferExecutionHistoryQueryPort {
    private final AutoTransferJpaRepository autoTransferJpaRepository;
    private final JPAQueryFactory queryFactory;


    @Override
    public AutoTransfer save(AutoTransfer autoTransfer) {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(AutoTransferMapper.toEntity(autoTransfer));
        return AutoTransferMapper.toDomain(saved);
    }

    @Override
    public Optional<AutoTransfer> findById(Long autoTransferId) {
        return autoTransferJpaRepository.findById(autoTransferId)
                .map(AutoTransferMapper::toDomain);
    }

    @Override
    public boolean existsActiveDuplicate(Long withdrawalAccountId, String depositAccountNumber, int transferDay) {
        return autoTransferJpaRepository.existsByWithdrawalAccountIdAndDepositAccountNumberAndTransferDayAndStatus(
                withdrawalAccountId, depositAccountNumber, transferDay, AutoTransferStatus.NORMAL);
    }

    @Override
    public Page<AutoTransfer> search(Long customerId, Long withdrawalAccountId, AutoTransferStatus status, Pageable pageable) {
        Predicate[] conditions = conditions(customerId, withdrawalAccountId, status);
        List<AutoTransferJpaEntity> content = queryFactory
                .selectFrom(autoTransferJpaEntity)
                .where(conditions)
                .orderBy(autoTransferJpaEntity.registeredAt.desc(), autoTransferJpaEntity.autoTransferId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory
                .select(autoTransferJpaEntity.count())
                .from(autoTransferJpaEntity)
                .where(conditions)
                .fetchOne();
        List<AutoTransfer> domainContent = content.stream().map(AutoTransferMapper::toDomain).toList();
        return new PageImpl<>(domainContent, pageable, total == null ? 0 : total);
    }

    // 정상 상태이고 오늘이거나 그 이전인 자동이체 등록 목록
    // 배치 루프 전체를 트랜잭션으로 감싸면 커넥션 풀을 고갈시킬 수 있다
    // -> 호출부는 트랜잭션이 없고, 조회 이 한 번만 여기서 짧게 트랜잭션을 걸어 끝낸다.
    @Override
    @Transactional(readOnly = true)
    public List<AutoTransfer> findDueForExecution(LocalDate date) {
        return queryFactory.selectFrom(autoTransferJpaEntity).where(dueForExecutionConditions(date))
                .orderBy(autoTransferJpaEntity.registeredAt.asc()).fetch().stream().map(AutoTransferMapper::toDomain)
                .toList();

    }

    private Predicate[] conditions(Long customerId, Long withdrawalAccountId, AutoTransferStatus status) {
        return new Predicate[]{
                // customerId까지 함께 걸어야 withdrawalAccountId만으로 타 고객 자동이체가 조회되는 것을 막을 수 있다(REQ-AUTO-009)
                autoTransferJpaEntity.customerId.eq(customerId),
                autoTransferJpaEntity.withdrawalAccountId.eq(withdrawalAccountId), statusEq(status)
        };
    }

    private Predicate[] dueForExecutionConditions(LocalDate date) {
        return new Predicate[]{
                autoTransferJpaEntity.status.eq(AutoTransferStatus.NORMAL),
                autoTransferJpaEntity.nextExecutionDate.loe(date)
        };
    }

    private BooleanExpression statusEq(AutoTransferStatus status) {
        return status != null ? autoTransferJpaEntity.status.eq(status) : null;
    }

    @Override
    // 회차 목록 한 페이지 조회
    public Page<AutoTransferExecutionHistoryRow> search(Long customerId, Long withdrawalAccountId,
                                                        LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Predicate[] conditions = executionHistoryConditions(customerId, withdrawalAccountId, fromDate, toDate);
        List<AutoTransferExecutionHistoryRow> content = queryFactory
                .select(Projections.constructor(AutoTransferExecutionHistoryRow.class,
                        autoTransferExecutionJpaEntity.executionId,
                        autoTransferExecutionJpaEntity.status,
                        autoTransferExecutionJpaEntity.executedAt,
                        autoTransferJpaEntity.withdrawalAccountId,
                        autoTransferJpaEntity.depositAccountNumber,
                        autoTransferJpaEntity.payeeName,
                        autoTransferExecutionJpaEntity.amount,
                        autoTransferJpaEntity.cycleMonths,
                        autoTransferJpaEntity.myPassbookMemo,
                        autoTransferExecutionJpaEntity.failureReason))
                .from(autoTransferExecutionJpaEntity)
                .join(autoTransferExecutionJpaEntity.autoTransfer, autoTransferJpaEntity)
                .where(conditions)
                .orderBy(autoTransferExecutionJpaEntity.executionDate.desc(),autoTransferExecutionJpaEntity.executedAt.desc(),autoTransferExecutionJpaEntity.executionId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(autoTransferExecutionJpaEntity.count())
                .from(autoTransferExecutionJpaEntity)
                .join(autoTransferExecutionJpaEntity.autoTransfer, autoTransferJpaEntity)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    // 정상/오류 처리 : 건수·금액 집계
    public AutoTransferExecutionHistoryAggregate summarize(Long customerId, Long withdrawalAccountId,
                                                           LocalDate fromDate, LocalDate toDate) {
        Predicate[] conditions = executionHistoryConditions(customerId, withdrawalAccountId, fromDate, toDate);
        List<Tuple> rows = queryFactory
                .select(autoTransferExecutionJpaEntity.status, autoTransferExecutionJpaEntity.count(),
                        autoTransferExecutionJpaEntity.amount.sumLong())
                .from(autoTransferExecutionJpaEntity)
                .join(autoTransferExecutionJpaEntity.autoTransfer, autoTransferJpaEntity)
                .where(conditions)
                .groupBy(autoTransferExecutionJpaEntity.status)
                .fetch();

        long successCount = 0L, successAmount = 0L, errorCount = 0L, errorAmount = 0L;
        for (Tuple row : rows) {
            ProcessResultStatus status = row.get(autoTransferExecutionJpaEntity.status);
            long count = row.get(autoTransferExecutionJpaEntity.count());
            Long amountSum = row.get(autoTransferExecutionJpaEntity.amount.sumLong());
            long amount = amountSum == null ? 0L : amountSum;
            if (status == ProcessResultStatus.SUCCESS) {
                successCount = count;
                successAmount = amount;
            } else if (status == ProcessResultStatus.ERROR) {
                errorCount = count;
                errorAmount = amount;
            }
        }
        return new AutoTransferExecutionHistoryAggregate(successCount, successAmount, errorCount, errorAmount);
    }

    // search()·summarize() 공통 조건: 소유자 확인 + 조회기간 + PROCESSING 제외
    private Predicate[] executionHistoryConditions(Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate) {
        return new Predicate[] {
                // customerId까지 함께 걸어야 withdrawalAccountId만으로 타 고객 자동이체가 조회되는 것을 막을 수 있다(REQ-AUTO-018)
                autoTransferJpaEntity.customerId.eq(customerId),
                autoTransferJpaEntity.withdrawalAccountId.eq(withdrawalAccountId),
                autoTransferExecutionJpaEntity.executionDate.goe(fromDate),
                autoTransferExecutionJpaEntity.executionDate.loe(toDate),
                // PROCESSING은 아직 확정 안 된 상태라 "결과"가 아님 — REQ-AUTO-018은 정상/오류만 결과로 규정
                autoTransferExecutionJpaEntity.status.in(ProcessResultStatus.SUCCESS, ProcessResultStatus.ERROR)
        };
    }

}
