package com.shinhan.corebank.transfer.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ledger_entry.ledger_entry_id 채번기.
 * Hibernate가 {@code @IdClass} 복합키 구성 필드의 IDENTITY 채번을 지원하지 않아
 * (docs/flyway_guide.md 4-3절 참고), 전용 카운터 테이블(ledger_entry_id_sequence)에
 * 빈 행을 저장해 얻은 AUTO_INCREMENT 값을 그대로 ledger_entry_id로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class LedgerEntryIdGenerator {

    private final LedgerEntryIdSequenceJpaRepository ledgerEntryIdSequenceJpaRepository;

    public Long nextId() {
        return ledgerEntryIdSequenceJpaRepository
                .save(new LedgerEntryIdSequenceJpaEntity())
                .getSequenceId();
    }
}
