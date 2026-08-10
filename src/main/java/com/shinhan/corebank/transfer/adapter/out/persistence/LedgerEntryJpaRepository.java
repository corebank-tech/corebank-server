package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, LedgerEntryId> {

    List<LedgerEntryJpaEntity> findByTransactionNumber(String transactionNumber);

    /**
     * ledgerEntryId는 전역 유일(AUTO_INCREMENT)이므로 occurredAt 없이도 단건 특정이 가능하다.
     * reversal_id가 가리키는 원거래 원장 행을 조회할 때 사용한다.
     */
    Optional<LedgerEntryJpaEntity> findByLedgerEntryId(Long ledgerEntryId);
}
