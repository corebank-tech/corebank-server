package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.application.port.out.LedgerBalanceQueryPort.LedgerBalancePoint;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerBalanceQueryPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Long ACCOUNT_ID = 8_811_001L;
    private static final Long OTHER_ACCOUNT_ID = 8_811_002L;

    @Autowired
    private LedgerBalanceQueryPersistenceAdapter adapter;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // 계산 시작일 이전 마지막 잔액
        save(ACCOUNT_ID, LocalDateTime.of(2026, 8, 31, 18, 0), 1_000L);

        // 계산 기간 내부
        save(ACCOUNT_ID, LocalDateTime.of(2026, 9, 1, 9, 0), 1_200L);

        save(ACCOUNT_ID, LocalDateTime.of(2026, 9, 1, 18, 0), 900L);

        save(ACCOUNT_ID, LocalDateTime.of(2026, 9, 3, 12, 0), 1_500L);

        // toExclusive 경계 — 포함되면 안 됨
        save(ACCOUNT_ID, LocalDateTime.of(2026, 9, 4, 0, 0), 1_600L);

        // 다른 계좌 — 포함되면 안 됨
        save(OTHER_ACCOUNT_ID, LocalDateTime.of(2026, 9, 2, 10, 0), 99_999L);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("계산 시작일 이전 가장 최근 원장의 balanceAfter를 조회한다")
    void findLatestBefore_returnsLatestBalanceBeforeStart() {
        LocalDateTime fromInclusive = LocalDateTime.of(2026, 9, 1, 0, 0);

        Optional<LedgerBalancePoint> result = adapter.findLatestBefore(ACCOUNT_ID, fromInclusive);

        assertThat(result).isPresent();
        assertThat(result.get().occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 31, 18, 0));
        assertThat(result.get().balanceAfter()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("계산 시작일 이전 원장이 없으면 빈 Optional을 반환한다")
    void findLatestBefore_returnsEmptyWhenNoPreviousLedger() {
        Optional<LedgerBalancePoint> result = adapter.findLatestBefore(ACCOUNT_ID, LocalDateTime.of(2026, 8, 1, 0, 0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("계좌와 반개구간에 해당하는 원장만 발생시각 오름차순으로 조회한다")
    void findBetween_returnsEntriesInsideRangeInAscendingOrder() {
        LocalDateTime fromInclusive = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime toExclusive = LocalDateTime.of(2026, 9, 4, 0, 0);

        List<LedgerBalancePoint> result = adapter.findBetween(ACCOUNT_ID, fromInclusive, toExclusive);

        assertThat(result).hasSize(3);

        assertThat(result)
                .extracting(LedgerBalancePoint::occurredAt)
                .containsExactly(
                        LocalDateTime.of(2026, 9, 1, 9, 0),
                        LocalDateTime.of(2026, 9, 1, 18, 0),
                        LocalDateTime.of(2026, 9, 3, 12, 0));

        assertThat(result).extracting(LedgerBalancePoint::balanceAfter).containsExactly(1_200L, 900L, 1_500L);
    }

    @Test
    @DisplayName("toExclusive 시각의 원장은 조회하지 않는다")
    void findBetween_excludesToExclusiveBoundary() {
        List<LedgerBalancePoint> result =
                adapter.findBetween(ACCOUNT_ID, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 4, 0, 0));

        assertThat(result).extracting(LedgerBalancePoint::balanceAfter).doesNotContain(1_600L);
    }

    @Test
    @DisplayName("같은 발생시각이면 ledgerEntryId 오름차순으로 조회한다")
    void findBetween_usesLedgerEntryIdAsTieBreaker() {
        LocalDateTime sameOccurredAt = LocalDateTime.of(2026, 9, 2, 15, 0);

        Long firstId = save(ACCOUNT_ID, sameOccurredAt, 2_000L);
        Long secondId = save(ACCOUNT_ID, sameOccurredAt, 2_500L);

        entityManager.flush();
        entityManager.clear();

        List<LedgerBalancePoint> result = adapter.findBetween(
                ACCOUNT_ID, LocalDateTime.of(2026, 9, 2, 15, 0), LocalDateTime.of(2026, 9, 2, 16, 0));

        assertThat(result).hasSize(2);

        assertThat(result).extracting(LedgerBalancePoint::ledgerEntryId).containsExactly(firstId, secondId);

        assertThat(result).extracting(LedgerBalancePoint::balanceAfter).containsExactly(2_000L, 2_500L);
    }

    private Long save(Long accountId, LocalDateTime occurredAt, long balanceAfter) {

        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();

        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(accountId)
                .transactionNumber(String.format("20260901WB%010d", ledgerEntryId))
                .direction(LedgerDirection.DEPOSIT)
                .amount(1_000L)
                .balanceAfter(balanceAfter)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build());

        return ledgerEntryId;
    }
}
