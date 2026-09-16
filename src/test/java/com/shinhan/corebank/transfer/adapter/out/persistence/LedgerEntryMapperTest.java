package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LedgerEntryMapper 단위 테스트")
class LedgerEntryMapperTest {

    @Test
    @DisplayName("LedgerEntry 도메인 객체를 LedgerEntryJpaEntity로 양방향 변환한다")
    void toEntityAndToDomainMapping() {
        // given
        LocalDateTime now = LocalDateTime.now();
        LedgerEntry domain = LedgerEntry.builder()
                .ledgerEntryId(1L)
                .accountId(101L)
                .transferId(10L)
                .transactionNumber("20260809WB0000000001")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(10000L)
                .balanceAfter(90000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent("출금통장메모")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(now)
                .build();

        // when
        LedgerEntryJpaEntity entity = LedgerEntryMapper.toEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getLedgerEntryId()).isEqualTo(1L);
        assertThat(entity.getAccountId()).isEqualTo(101L);
        assertThat(entity.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
        assertThat(entity.getChannel()).isEqualTo(TransferChannel.WB);

        // when (toDomain)
        LedgerEntry mappedDomain = LedgerEntryMapper.toDomain(entity);

        // then
        assertThat(mappedDomain).isNotNull();
        assertThat(mappedDomain.getLedgerEntryId()).isEqualTo(domain.getLedgerEntryId());
        assertThat(mappedDomain.getAccountId()).isEqualTo(domain.getAccountId());
        assertThat(mappedDomain.getDirection()).isEqualTo(domain.getDirection());
        assertThat(mappedDomain.getChannel()).isEqualTo(TransferChannel.WB);
    }
}
