package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferExecutionResultAggregate;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferBatchQueryPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.QScheduledTransferJpaEntity.scheduledTransferJpaEntity;

@Repository
@RequiredArgsConstructor
public class ScheduledTransferPersistenceAdapter implements ScheduledTransferPersistencePort, ScheduledTransferQueryPort, ScheduledTransferBatchQueryPort {
    private final ScheduledTransferJpaRepository scheduledTransferJpaRepository;
    private final JPAQueryFactory queryFactory;


    @Override
    public boolean claimForProcessing(Long scheduledTransferId) {
        return scheduledTransferJpaRepository.claimForProcessing(scheduledTransferId) > 0;
    }

    @Override
    public List<ScheduledTransfer> findDueForExecution(LocalDate date) {
        return scheduledTransferJpaRepository
                .findByStatusAndScheduledDateOrderByRegisteredAtAsc(ScheduledTransferStatus.WAITING, date)
                .stream()
                .map(ScheduledTransferMapper::toDomain)
                .toList();
    }

    @Override
    public boolean saveIfStillProcessing(ScheduledTransfer scheduledTransfer) {
        int updated = scheduledTransferJpaRepository.finalizeIfProcessing(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getStatus().name(),
                scheduledTransfer.getTransactionNumber(),
                scheduledTransfer.getFailureReason(),
                scheduledTransfer.getExecutedAt());
        return updated > 0;
    }

    @Override
    public List<ScheduledTransfer> findAllProcessing() {
        return scheduledTransferJpaRepository.findByStatus(ScheduledTransferStatus.PROCESSING)
                .stream()
                .map(ScheduledTransferMapper::toDomain)
                .toList();
    }
    @Override
    public ScheduledTransfer save(ScheduledTransfer scheduledTransfer) {
        try {
            ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(ScheduledTransferMapper.toEntity(scheduledTransfer));
            return ScheduledTransferMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // 사전 existsActiveDuplicate() 확인과 INSERT 사이의 동시성 경쟁 — DB unique 제약(uk_sched_active_dup)이
            // 최종 방어선. 그 위반만 골라 SCD0301로 변환하고, 다른 무결성 위반은 그대로 전파한다.
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_sched_active_dup")) {
                throw new BusinessException(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION);
            }
            throw e;
        }
    }

    @Override
    public Optional<ScheduledTransfer> findById(Long scheduledTransferId) {
        return scheduledTransferJpaRepository.findById(scheduledTransferId)
                .map(ScheduledTransferMapper::toDomain);
    }

    @Override
    public boolean existsActiveDuplicate(Long customerId, Long withdrawalAccountId, String payeeAccountNumber,
                                         Long amount, LocalDate scheduledDate) {
        return scheduledTransferJpaRepository
                .existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
                        customerId, withdrawalAccountId, payeeAccountNumber, amount, scheduledDate,
                        ScheduledTransferStatus.WAITING);
    }

    @Override
    public Page<ScheduledTransfer> search(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                          LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Predicate[] conditions = conditions(customerId, status, withdrawalAccountId, fromDate, toDate);
        List<ScheduledTransferJpaEntity> content = queryFactory
                .selectFrom(scheduledTransferJpaEntity)
                .where(conditions)
                .orderBy(scheduledTransferJpaEntity.scheduledDate.asc(), scheduledTransferJpaEntity.scheduledTransferId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory
                .select(scheduledTransferJpaEntity.count())
                .from(scheduledTransferJpaEntity)
                .where(conditions)
                .fetchOne();
        List<ScheduledTransfer> domainContent = content.stream().map(ScheduledTransferMapper::toDomain).toList();
        return new PageImpl<>(domainContent, pageable, total == null ? 0 : total);
    }

    @Override
    public Page<ScheduledTransfer> searchExecutionResults(Long customerId, Long withdrawalAccountId,
                                                          LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Predicate[] conditions = executionResultConditions(customerId, withdrawalAccountId, fromDate, toDate);
        List<ScheduledTransferJpaEntity> content = queryFactory
                .selectFrom(scheduledTransferJpaEntity)
                .where(conditions)
                .orderBy(scheduledTransferJpaEntity.scheduledDate.desc(), scheduledTransferJpaEntity.scheduledTransferId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory
                .select(scheduledTransferJpaEntity.count())
                .from(scheduledTransferJpaEntity)
                .where(conditions)
                .fetchOne();
        List<ScheduledTransfer> domainContent = content.stream().map(ScheduledTransferMapper::toDomain).toList();
        return new PageImpl<>(domainContent, pageable, total == null ? 0 : total);
    }

    @Override
    // 정상/오류/취소 : 건수·금액 집계
    public ScheduledTransferExecutionResultAggregate summarizeExecutionResults(Long customerId, Long withdrawalAccountId,
                                                                                LocalDate fromDate, LocalDate toDate) {
        Predicate[] conditions = executionResultConditions(customerId, withdrawalAccountId, fromDate, toDate);
        List<Tuple> rows = queryFactory
                .select(scheduledTransferJpaEntity.status, scheduledTransferJpaEntity.count(),
                        scheduledTransferJpaEntity.amount.sumLong())
                .from(scheduledTransferJpaEntity)
                .where(conditions)
                .groupBy(scheduledTransferJpaEntity.status)
                .fetch();

        long successCount = 0L, successAmount = 0L, failedCount = 0L, failedAmount = 0L, canceledCount = 0L, canceledAmount = 0L;
        for (Tuple row : rows) {
            ScheduledTransferStatus status = row.get(scheduledTransferJpaEntity.status);
            long count = row.get(scheduledTransferJpaEntity.count());
            Long amountSum = row.get(scheduledTransferJpaEntity.amount.sumLong());
            long amount = amountSum == null ? 0L : amountSum;
            if (status == ScheduledTransferStatus.SUCCESS) {
                successCount = count;
                successAmount = amount;
            } else if (status == ScheduledTransferStatus.FAILED) {
                failedCount = count;
                failedAmount = amount;
            } else if (status == ScheduledTransferStatus.CANCELED) {
                canceledCount = count;
                canceledAmount = amount;
            }
        }
        return new ScheduledTransferExecutionResultAggregate(successCount, successAmount, failedCount, failedAmount,
                canceledCount, canceledAmount);
    }

    private Predicate[] conditions(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                   LocalDate fromDate, LocalDate toDate) {
        return new Predicate[]{
                scheduledTransferJpaEntity.customerId.eq(customerId),
                statusEq(status),
                withdrawalAccountIdEq(withdrawalAccountId),
                scheduledDateGoe(fromDate),
                scheduledDateLoe(toDate)
        };
    }

    // search()/summarize() 공통 조건: 소유자 확인 + 조회기간 + WAITING/PROCESSING 제외
    private Predicate[] executionResultConditions(Long customerId, Long withdrawalAccountId,
                                                   LocalDate fromDate, LocalDate toDate) {
        return new Predicate[]{
                scheduledTransferJpaEntity.customerId.eq(customerId),
                withdrawalAccountIdEq(withdrawalAccountId),
                scheduledDateGoe(fromDate),
                scheduledDateLoe(toDate),
                // WAITING/PROCESSING은 아직 확정 안 된 상태라 "결과"가 아님 (REQ-SCD-014)
                scheduledTransferJpaEntity.status.in(ScheduledTransferStatus.SUCCESS, ScheduledTransferStatus.FAILED,
                        ScheduledTransferStatus.CANCELED)
        };
    }

    private BooleanExpression statusEq(ScheduledTransferStatus status) {
        return status != null ? scheduledTransferJpaEntity.status.eq(status) : null;
    }

    private BooleanExpression withdrawalAccountIdEq(Long withdrawalAccountId) {
        return withdrawalAccountId != null ? scheduledTransferJpaEntity.withdrawalAccountId.eq(withdrawalAccountId) : null;
    }

    private BooleanExpression scheduledDateGoe(LocalDate fromDate) {
        return fromDate != null ? scheduledTransferJpaEntity.scheduledDate.goe(fromDate) : null;
    }

    private BooleanExpression scheduledDateLoe(LocalDate toDate) {
        return toDate != null ? scheduledTransferJpaEntity.scheduledDate.loe(toDate) : null;
    }
}
