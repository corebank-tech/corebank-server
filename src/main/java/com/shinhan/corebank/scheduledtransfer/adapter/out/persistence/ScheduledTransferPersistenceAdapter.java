package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
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
public class ScheduledTransferPersistenceAdapter implements ScheduledTransferPersistencePort, ScheduledTransferQueryPort {
    private final ScheduledTransferJpaRepository scheduledTransferJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ScheduledTransfer save(ScheduledTransfer scheduledTransfer) {
        ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(ScheduledTransferMapper.toEntity(scheduledTransfer));
        return ScheduledTransferMapper.toDomain(saved);
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
