package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.subscription.application.port.in.AccumulatedDailyBalanceUseCase;
import com.shinhan.corebank.transfer.application.port.out.LedgerAccumulatedBalancePort;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerAccumulatedBalancePersistenceAdapterTest extends IntegrationTestSupport {

    private static final long ACCOUNT_A = 8_819_001L;
    private static final long ACCOUNT_B = 8_819_002L;
    private static final long ACCOUNT_C = 8_819_003L;
    private static final long ACCOUNT_D = 8_819_004L;

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

    private static final LocalDate TO = LocalDate.of(2026, 9, 4);

    @Autowired
    private LedgerAccumulatedBalancePort sqlPort;

    @Autowired
    private AccumulatedDailyBalanceUseCase oldUseCase;

    @Autowired
    private LedgerEntryJpaRepository entries;

    @Autowired
    private LedgerEntryIdGenerator ids;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("SQL 집계는 기존 PH-11과 동일한 적수를 반환한다")
    void calculate_matchesLegacyForMultipleAccounts() {

        // A: 시작 잔액 + 동일 날짜 여러 거래
        save(ACCOUNT_A, LocalDateTime.of(2026, 8, 31, 18, 0), 100L);

        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 1, 9, 0), 200L);

        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 1, 18, 0), 300L);

        // 동일 발생 시각이면 ledgerEntryId가 큰 행 우선
        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 1, 18, 0), 350L);

        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 3, 12, 0), 500L);

        // 종료일은 계산에서 제외
        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 4, 0, 0), 999L);

        // B: 시작 잔액 없음
        save(ACCOUNT_B, LocalDateTime.of(2026, 9, 2, 10, 0), 700L);

        // D: 기간 중 거래 없음
        save(ACCOUNT_D, LocalDateTime.of(2026, 8, 30, 10, 0), 900L);

        // C: 원장 없음 → 적수 0

        entityManager.flush();
        entityManager.clear();

        List<Long> accounts = List.of(ACCOUNT_A, ACCOUNT_B, ACCOUNT_C, ACCOUNT_D);

        Map<Long, Long> actual = sqlPort.calculate(accounts, FROM, TO);

        assertThat(actual)
                .containsEntry(ACCOUNT_A, 1_200L)
                .containsEntry(ACCOUNT_B, 1_400L)
                .containsEntry(ACCOUNT_C, 0L)
                .containsEntry(ACCOUNT_D, 2_700L);

        // 각 계좌의 결과를 기존 PH-11 계산과 직접 비교
        for (long accountId : accounts) {
            assertThat(actual.get(accountId))
                    .as("accountId=%s", accountId)
                    .isEqualTo(oldUseCase.calculate(accountId, FROM, TO));
        }
    }

    @Test
    @DisplayName("거래일이 달라도 기존과 동일하게 발생일 기준으로 계산한다")
    void calculate_usesOccurredAtNotTradeDate() {

        save(ACCOUNT_A, LocalDateTime.of(2026, 9, 2, 10, 0), 500L, LocalDate.of(2026, 9, 1));

        entityManager.flush();
        entityManager.clear();

        long actual = sqlPort.calculate(List.of(ACCOUNT_A), FROM, TO).get(ACCOUNT_A);

        assertThat(actual).isEqualTo(1_000L);

        assertThat(actual).isEqualTo(oldUseCase.calculate(ACCOUNT_A, FROM, TO));
    }

    @Test
    @DisplayName("빈 계산 기간은 적수 0을 반환한다")
    void calculate_emptyDateInterval() {

        assertThat(sqlPort.calculate(List.of(ACCOUNT_A, ACCOUNT_B), FROM, FROM))
                .containsEntry(ACCOUNT_A, 0L)
                .containsEntry(ACCOUNT_B, 0L);
    }

    private void save(long accountId, LocalDateTime occurredAt, long balanceAfter) {

        save(accountId, occurredAt, balanceAfter, null);
    }

    private void save(long accountId, LocalDateTime occurredAt, long balanceAfter, LocalDate tradeDate) {

        Long entryId = ids.nextId();

        entries.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(entryId)
                .accountId(accountId)
                .transactionNumber(String.format("20260901WB%010d", entryId))
                .direction(LedgerDirection.DEPOSIT)
                .amount(1_000L)
                .balanceAfter(balanceAfter)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .tradeDate(tradeDate)
                .occurredAt(occurredAt)
                .build());
    }
}
