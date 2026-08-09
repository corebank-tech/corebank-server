package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class LedgerEntryJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("@IdClass(LedgerEntryId.class) 적용 원장 엔티티를 영속화하고 복합키로 정상 조회한다")
    void saveAndFindByCompositeKey() {
        // given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 9, 12, 0, 0);
        LedgerEntryJpaEntity entity = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(1L)
                .accountId(101L)
                .transactionNumber("20260809WB0000000001")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(10000L)
                .balanceAfter(90000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent("이체출금")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build();

        // when
        LedgerEntryJpaEntity saved = ledgerEntryJpaRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(saved.getLedgerEntryId()).isNotNull();

        LedgerEntryId id = new LedgerEntryId(saved.getLedgerEntryId(), occurredAt);
        LedgerEntryJpaEntity found = ledgerEntryJpaRepository.findById(id).orElseThrow();

        assertThat(found.getAccountId()).isEqualTo(101L);
        assertThat(found.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
        assertThat(found.getAmount()).isEqualTo(10000L);
        assertThat(found.getTransactionNumber()).isEqualTo("20260809WB0000000001");
        assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
    }
}
