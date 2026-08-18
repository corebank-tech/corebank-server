package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ledger_entry.ledger_entry_id 채번 전용 카운터 엔티티.
 * 저장할 다른 데이터는 없고, save() 호출마다 새 행을 만들어 AUTO_INCREMENT로
 * 생성된 sequenceId 값만 꺼내 쓴다. 단일 @Id라 IDENTITY 채번이 정상 동작한다
 * (ledger_entry 자체는 @IdClass 복합키라 IDENTITY를 쓸 수 없는 것과 대비됨).
 * 실제 사용은 {@link LedgerEntryIdGenerator} 참고.
 */
@Entity
@Table(name = "ledger_entry_id_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntryIdSequenceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id")
    private Long sequenceId;
}
