package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TransactionSequenceJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private TransactionSequenceJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("영업일자 및 채널로 비관적 락(findForUpdate) 조회 시 해당 레코드가 정상 반환된다")
    void findBySeqDateAndChannelForUpdate_returnsSequenceEntity() {
        // given
        LocalDate seqDate = LocalDate.of(2026, 8, 9);
        String channel = "WB";
        TransactionSequenceJpaEntity entity = TransactionSequenceJpaEntity.builder()
                .seqDate(seqDate)
                .channel(channel)
                .lastSeq(100L)
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // when
        TransactionSequenceJpaEntity found = repository
                .findBySeqDateAndChannelForUpdate(seqDate, channel)
                .orElseThrow();

        // then
        assertThat(found.getSeqDate()).isEqualTo(seqDate);
        assertThat(found.getChannel()).isEqualTo(channel);
        assertThat(found.getLastSeq()).isEqualTo(100L);
    }
}
