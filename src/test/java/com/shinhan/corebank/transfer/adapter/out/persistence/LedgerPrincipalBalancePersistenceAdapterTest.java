package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerPrincipalBalancePersistenceAdapterTest extends IntegrationTestSupport {

    private static final long ACCOUNT_ID = 730_001L;
    private static final long OTHER_ACCOUNT_ID = 730_002L;

    @Autowired
    private LedgerPrincipalBalanceQuery principalBalanceQuery;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("여러 회차 납입을 합산하고 다른 계좌와 이자 거래를 제외한다")
    void sumMultipleDepositsExcludingInterest() {
        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 100_000L, "AUTO_TRANSFER", false, null, at(1));

        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 100_000L, "AUTO_TRANSFER", false, null, at(2));

        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 100_000L, "AUTO_TRANSFER", false, null, at(3));

        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 3_000L, "INTEREST", false, null, at(4));

        save(OTHER_ACCOUNT_ID, LedgerDirection.DEPOSIT, 500_000L, "AUTO_TRANSFER", false, null, at(5));

        flushAndClear();

        assertThat(principalBalanceQuery.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .isEqualTo(300_000L);
    }

    @Test
    @DisplayName("원거래 취소와 정정 후 재기표를 순합계에 반영한다")
    void sumReversalAndRepost() {
        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 100_000L, "AUTO_TRANSFER", false, null, at(1));

        long originalId = save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 100_000L, "AUTO_TRANSFER", true, null, at(2));

        save(ACCOUNT_ID, LedgerDirection.WITHDRAWAL, 100_000L, "REVERSAL", false, originalId, at(3));

        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 70_000L, "AUTO_TRANSFER", false, null, at(4));

        flushAndClear();

        // 100,000 + 100,000 - 100,000 + 70,000
        assertThat(principalBalanceQuery.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .isEqualTo(170_000L);
    }

    @Test
    @DisplayName("원장 거래가 없으면 원금 0원을 반환한다")
    void sumNoEntries() {
        assertThat(principalBalanceQuery.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .isZero();
    }

    @Test
    @DisplayName("일반 출금도 원금 순합계에서 차감한다")
    void sumDepositAndWithdrawal() {
        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 200_000L, "AUTO_TRANSFER", false, null, at(1));

        save(ACCOUNT_ID, LedgerDirection.WITHDRAWAL, 50_000L, "IMMEDIATE_TRANSFER", false, null, at(2));

        flushAndClear();

        assertThat(principalBalanceQuery.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .isEqualTo(150_000L);
    }

    private long save(
            long accountId,
            LedgerDirection direction,
            long amount,
            String transactionType,
            boolean reversed,
            Long reversalId,
            LocalDateTime occurredAt) {

        long entryId = ledgerEntryIdGenerator.nextId();

        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(entryId)
                .accountId(accountId)
                .transactionNumber(String.format("20260301BT%010d", entryId % 10_000_000_000L))
                .direction(direction)
                .amount(amount)
                .balanceAfter(100_000L)
                .transactionType(transactionType)
                .channel(TransferChannel.BT)
                .tradeDate(occurredAt.toLocalDate())
                .reversed(reversed)
                .reversalId(reversalId)
                .occurredAt(occurredAt)
                .build());

        return entryId;
    }

    private static LocalDateTime at(int day) {
        return LocalDateTime.of(2026, 3, day, 9, 0);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
