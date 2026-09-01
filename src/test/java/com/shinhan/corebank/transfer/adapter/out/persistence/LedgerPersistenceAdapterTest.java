package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private LedgerPersistenceAdapter adapter;

    @Autowired
    private LedgerEntryJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("LedgerPair를 저장하면 출금·입금 2행이 서로 다른 ledgerEntryId로 저장된다")
    void save_persistsBothWithdrawalAndDepositEntries() {
        // given
        LedgerPair pair = LedgerPair.forTransfer(
                500L,
                "20260812WB0000000003",
                101L,
                90000L,
                202L,
                110000L,
                10000L,
                "IMMEDIATE_TRANSFER",
                "이체출금",
                "이체입금",
                TransferChannel.WB,
                LocalDateTime.now());

        // when
        adapter.save(pair);
        entityManager.flush();
        entityManager.clear();

        // then
        List<LedgerEntryJpaEntity> entries = repository.findByTransactionNumber("20260812WB0000000003");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getLedgerEntryId())
                .isNotEqualTo(entries.get(1).getLedgerEntryId());

        LedgerEntryJpaEntity withdrawal = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.WITHDRAWAL)
                .findFirst()
                .orElseThrow();
        LedgerEntryJpaEntity deposit = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.DEPOSIT)
                .findFirst()
                .orElseThrow();

        assertThat(withdrawal.getAccountId()).isEqualTo(101L);
        assertThat(withdrawal.getBalanceAfter()).isEqualTo(90000L);
        assertThat(deposit.getAccountId()).isEqualTo(202L);
        assertThat(deposit.getBalanceAfter()).isEqualTo(110000L);
        assertThat(withdrawal.getTransferId()).isEqualTo(500L);
        assertThat(deposit.getTransferId()).isEqualTo(500L);
    }
}
