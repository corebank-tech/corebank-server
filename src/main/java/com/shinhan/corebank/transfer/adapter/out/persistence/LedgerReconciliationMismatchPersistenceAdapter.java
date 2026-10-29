package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LedgerReconciliationMismatchPersistenceAdapter implements LedgerReconciliationMismatchPort {

    private final LedgerReconciliationMismatchJpaRepository repository;

    public LedgerReconciliationMismatchPersistenceAdapter(LedgerReconciliationMismatchJpaRepository repository) {
        this.repository = repository;
    }

    // 확인과 저장 사이 경합은 대사 락이 막고, 그래도 겹치면 (영업일, 계좌) 유니크 키가 막는다.
    @Override
    @Transactional
    public boolean saveIfAbsent(
            LocalDate businessDate, List<LedgerReconciliationMismatch> mismatches, LocalDateTime detectedAt) {
        if (repository.existsByBusinessDate(businessDate)) {
            return false;
        }
        repository.saveAll(mismatches.stream()
                .map(mismatch -> LedgerReconciliationMismatchJpaEntity.builder()
                        .businessDate(businessDate)
                        .accountId(mismatch.accountId())
                        .ledgerBalance(mismatch.ledgerBalance())
                        .accountBalance(mismatch.accountBalance())
                        .detectedAt(detectedAt)
                        .build())
                .toList());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findLatestBusinessDate() {
        return repository.findLatestBusinessDate();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByBusinessDate(LocalDate businessDate) {
        return repository.countByBusinessDate(businessDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerReconciliationMismatchView> findByBusinessDate(LocalDate businessDate, Pageable pageable) {
        return repository
                .findByBusinessDateOrderByAccountIdAsc(businessDate, pageable)
                .map(entity -> new LedgerReconciliationMismatchView(
                        entity.getAccountId(),
                        entity.getLedgerBalance(),
                        entity.getAccountBalance(),
                        entity.getDetectedAt()));
    }
}
