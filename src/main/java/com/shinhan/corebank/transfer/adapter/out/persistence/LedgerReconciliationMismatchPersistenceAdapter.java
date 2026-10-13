package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LedgerReconciliationMismatchPersistenceAdapter implements LedgerReconciliationMismatchPort {

    private final LedgerReconciliationMismatchJpaRepository repository;

    public LedgerReconciliationMismatchPersistenceAdapter(LedgerReconciliationMismatchJpaRepository repository) {
        this.repository = repository;
    }

    // 삭제와 삽입이 따로 커밋되면 "지웠는데 못 넣은" 상태가 보일 수 있어 한 트랜잭션으로 묶는다.
    @Override
    @Transactional
    public void replaceForBusinessDate(
            LocalDate businessDate, List<LedgerReconciliationMismatch> mismatches, LocalDateTime detectedAt) {
        repository.deleteByBusinessDate(businessDate);
        repository.saveAll(mismatches.stream()
                .map(mismatch -> LedgerReconciliationMismatchJpaEntity.builder()
                        .businessDate(businessDate)
                        .accountId(mismatch.accountId())
                        .ledgerBalance(mismatch.ledgerBalance())
                        .accountBalance(mismatch.accountBalance())
                        .detectedAt(detectedAt)
                        .build())
                .toList());
    }
}
