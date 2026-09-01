package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, LedgerEntryId> {

    List<LedgerEntryJpaEntity> findByTransactionNumber(String transactionNumber);

    /**
     * ledgerEntryId 단건 조회. reversal_id가 가리키는 원거래 원장 행을 occurredAt 없이 조회할 때 사용한다.
     *
     * 주의: ledger_entry의 실제 PK는 (ledger_entry_id, occurred_at) 복합키이고,
     * ledger_entry는 파티션 테이블이라 파티션 키(occurred_at)가 모든 UNIQUE 인덱스에 포함돼야
     * 하는 MySQL 제약 때문에 ledger_entry_id 단독 UNIQUE 인덱스를 DB에 걸 수 없다(다른 파티션에
     * 동일 ledger_entry_id가 들어가는 걸 DB가 막아주지 않는다). 이 메서드가 항상 단건만 반환한다는
     * 전제는 {@link LedgerEntryIdGenerator}로만 ledgerEntryId를 채번한다는 애플리케이션 규율에
     * 의존하며, DB 제약으로 강제되지 않는다. 이 규율이 깨지면 둘 이상의 행이 매칭돼
     * {@code IncorrectResultSizeDataAccessException}이 발생한다.
     */
    Optional<LedgerEntryJpaEntity> findByLedgerEntryId(Long ledgerEntryId);
}
