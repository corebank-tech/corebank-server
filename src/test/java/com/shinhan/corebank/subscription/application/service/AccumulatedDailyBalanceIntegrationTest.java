package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryIdGenerator;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaEntity;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaRepository;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AccumulatedDailyBalanceIntegrationTest extends IntegrationTestSupport {

    private static final Long ACCOUNT_ID = 8_812_001L;

    @Autowired
    private AccumulatedDailyBalanceUseCase accumulatedDailyBalanceUseCase;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("원장 조회부터 일말잔액 이월까지 전체 경로로 적수를 계산한다")
    void calculateAccumulatedDailyBalance_endToEnd() {
        // 계산 시작일 이전 마지막 잔액
        save(LocalDateTime.of(2026, 8, 31, 18, 0), 1_000L);

        // 9/1 같은 날 거래 2건 → 마지막 900원이 일말잔액
        save(LocalDateTime.of(2026, 9, 1, 9, 0), 1_200L);

        save(LocalDateTime.of(2026, 9, 1, 18, 0), 900L);

        // 9/2 거래 없음 → 900원 이월

        // 9/3 거래
        save(LocalDateTime.of(2026, 9, 3, 12, 0), 1_500L);

        // 종료 경계. 계산에 포함되면 안 됨
        save(LocalDateTime.of(2026, 9, 4, 0, 0), 2_000L);

        entityManager.flush();
        entityManager.clear();

        long result = accumulatedDailyBalanceUseCase.calculate(
                ACCOUNT_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4));

        // 9/1 = 900
        // 9/2 = 900
        // 9/3 = 1,500
        assertThat(result).isEqualTo(3_300L);
    }

    @Test
    @DisplayName("계산 시작일 이전 원장이 없으면 시작 잔액 0원으로 계산한다")
    void calculateAccumulatedDailyBalance_withoutOpeningBalance() {
        save(LocalDateTime.of(2026, 9, 2, 10, 0), 500L);

        entityManager.flush();
        entityManager.clear();

        long result = accumulatedDailyBalanceUseCase.calculate(
                ACCOUNT_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4));

        // 9/1 = 0
        // 9/2 = 500
        // 9/3 = 500
        assertThat(result).isEqualTo(1_000L);
    }

    private void save(LocalDateTime occurredAt, long balanceAfter) {

        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();

        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(ACCOUNT_ID)
                .transactionNumber(String.format("20260901WB%010d", ledgerEntryId))
                .direction(LedgerDirection.DEPOSIT)
                .amount(1_000L)
                .balanceAfter(balanceAfter)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build());
    }
}
