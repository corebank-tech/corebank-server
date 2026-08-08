package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shinhan.corebank.autotransfer.adapter.out.persistence.QAutoTransferJpaEntity.autoTransferJpaEntity;

@Repository
@RequiredArgsConstructor
// 이 파일은 DB에 직접 접근하게 해주는 파일
public class AutoTransferPersistenceAdapter implements AutoTransferPersistencePort, AutoTransferQueryPort {
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
                withdrawalAccountId,depositAccountNumber, transferDay, AutoTransferStatus.NORMAL);
    }

    @Override
    public Page<AutoTransfer> search(Long withdrawalAccountId, AutoTransferStatus status, Pageable pageable) {
        Predicate[] conditions = conditions(withdrawalAccountId, status);
        List<AutoTransferJpaEntity> content = queryFactory
                .selectFrom(autoTransferJpaEntity)
                .where(conditions)
                .orderBy(autoTransferJpaEntity.registeredAt.desc())
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

    private Predicate[] conditions(Long withdrawalAccountId, AutoTransferStatus status) {
        return new Predicate[] {
                autoTransferJpaEntity.withdrawalAccountId.eq(withdrawalAccountId),statusEq(status)
        };
    }

    private BooleanExpression statusEq(AutoTransferStatus status) {
        return status != null ? autoTransferJpaEntity.status.eq(status) : null;
    }
}
