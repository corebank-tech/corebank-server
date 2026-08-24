package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryAggregate;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class LedgerHistoryQueryPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private LedgerHistoryQueryPersistenceAdapter adapter;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    private static final Long ACCOUNT_ID = 101L;
    private static final Long OTHER_ACCOUNT_ID = 202L;

    @BeforeEach
    void setUp() {
        // account 101: 7/10 출금 1만, 7/15 입금 2만("용돈"), 8/5 출금 5천 (조회기간 밖)
        save(ACCOUNT_ID, LedgerDirection.WITHDRAWAL, 10_000L, "20260710WB0000000001",
                "이체출금", LocalDateTime.of(2026, 7, 10, 10, 0, 0));
        save(ACCOUNT_ID, LedgerDirection.DEPOSIT, 20_000L, "20260715WB0000000001",
                "용돈", LocalDateTime.of(2026, 7, 15, 10, 0, 0));
        save(ACCOUNT_ID, LedgerDirection.WITHDRAWAL, 5_000L, "20260805WB0000000001",
                "이체출금", LocalDateTime.of(2026, 8, 5, 10, 0, 0));
        // 다른 계좌 — 절대 섞이면 안 됨
        save(OTHER_ACCOUNT_ID, LedgerDirection.DEPOSIT, 99_999L, "20260712WB0000000009",
                "남의돈", LocalDateTime.of(2026, 7, 12, 10, 0, 0));

        entityManager.flush();
        entityManager.clear();
    }

    private void save(Long accountId, LedgerDirection direction, long amount, String txNo,
            String content, LocalDateTime occurredAt) {
        LedgerEntryJpaEntity entity = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(accountId)
                .transactionNumber(txNo)
                .direction(direction)
                .amount(amount)
                .balanceAfter(1_000_000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent(content)
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build();
        ledgerEntryJpaRepository.save(entity);
    }

    @Test
    @DisplayName("계좌·기간으로 조회하면 다른 계좌와 기간 밖 거래는 제외되고 최신순으로 정렬된다")
    void findEntries_filtersByAccountAndDateRange_sortedLatestFirst() {
        LedgerHistoryQuery query = julyQuery(LedgerHistoryDirection.ALL, null, LedgerHistorySort.LATEST, 0, 10);

        List<LedgerEntry> entries = adapter.findEntries(query);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getTransactionNumber()).isEqualTo("20260715WB0000000001"); // 최신
        assertThat(entries.get(1).getTransactionNumber()).isEqualTo("20260710WB0000000001");
    }

    @Test
    @DisplayName("keyword로 적요 부분일치 검색을 하면 일치하는 거래만 반환된다")
    void findEntries_filtersByKeyword() {
        LedgerHistoryQuery query = julyQuery(LedgerHistoryDirection.ALL, "용돈", LedgerHistorySort.LATEST, 0, 10);

        List<LedgerEntry> entries = adapter.findEntries(query);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTransactionContent()).isEqualTo("용돈");
    }

    @Test
    @DisplayName("all=true면 page/size 무관하게 조건에 맞는 전체 건이 반환된다")
    void findEntries_all_returnsAllMatchingRows() {
        LedgerHistoryQuery query = LedgerHistoryQuery.builder()
                .accountId(ACCOUNT_ID)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 7, 31))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(0)
                .all(true)
                .build();

        List<LedgerEntry> entries = adapter.findEntries(query);

        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("페이지 크기 1로 조회하면 1건만 반환되지만 전체 건수는 여전히 2건이다")
    void countEntries_ignoresPaging() {
        LedgerHistoryQuery pagedQuery = julyQuery(LedgerHistoryDirection.ALL, null, LedgerHistorySort.LATEST, 0, 1);

        List<LedgerEntry> entries = adapter.findEntries(pagedQuery);
        long total = adapter.countEntries(pagedQuery);

        assertThat(entries).hasSize(1);
        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("summarize는 direction 필터를 반영해 그 조건에 해당하는 입출금 건수·금액만 집계한다")
    void summarize_reflectsDirectionFilter() {
        LedgerHistoryQuery allQuery = julyQuery(LedgerHistoryDirection.ALL, null, LedgerHistorySort.LATEST, 0, 10);
        LedgerHistoryQuery withdrawalOnlyQuery = julyQuery(LedgerHistoryDirection.WITHDRAWAL, null, LedgerHistorySort.LATEST, 0, 10);

        LedgerHistoryAggregate all = adapter.summarize(allQuery);
        LedgerHistoryAggregate withdrawalOnly = adapter.summarize(withdrawalOnlyQuery);

        assertThat(all.depositCount()).isEqualTo(1);
        assertThat(all.depositAmount()).isEqualTo(20_000L);
        assertThat(all.withdrawalCount()).isEqualTo(1);
        assertThat(all.withdrawalAmount()).isEqualTo(10_000L);

        assertThat(withdrawalOnly.depositCount()).isEqualTo(0);
        assertThat(withdrawalOnly.depositAmount()).isEqualTo(0);
        assertThat(withdrawalOnly.withdrawalCount()).isEqualTo(1);
        assertThat(withdrawalOnly.withdrawalAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("취소된 원거래(reversed=true)와 반대기표(REVERSAL) 모두 목록에서 필터링되지 않고 노출된다 (정책 A)")
    void findEntries_doesNotExcludeReversedOrReversalEntries() {
        LedgerEntryJpaEntity original = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(ACCOUNT_ID)
                .transactionNumber("20260720WB0000000001")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(7_000L)
                .balanceAfter(1_000_000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent("취소된거래")
                .channel(TransferChannel.WB)
                .reversed(true)
                .occurredAt(LocalDateTime.of(2026, 7, 20, 9, 0, 0))
                .build();
        LedgerEntryJpaEntity savedOriginal = ledgerEntryJpaRepository.save(original);

        LedgerEntryJpaEntity reversal = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(ACCOUNT_ID)
                .transactionNumber("20260720WB0000000002")
                .direction(LedgerDirection.DEPOSIT)
                .amount(7_000L)
                .balanceAfter(1_007_000L)
                .transactionType("REVERSAL")
                .transactionContent("반대기표")
                .channel(TransferChannel.WB)
                .reversed(false)
                .reversalId(savedOriginal.getLedgerEntryId())
                .occurredAt(LocalDateTime.of(2026, 7, 20, 9, 0, 1))
                .build();
        ledgerEntryJpaRepository.save(reversal);
        entityManager.flush();
        entityManager.clear();

        LedgerHistoryQuery query = julyQuery(LedgerHistoryDirection.ALL, null, LedgerHistorySort.LATEST, 0, 10);

        List<LedgerEntry> entries = adapter.findEntries(query);

        assertThat(entries)
                .extracting(LedgerEntry::getTransactionNumber)
                .contains("20260720WB0000000001", "20260720WB0000000002");
    }

    @Test
    @DisplayName("summarize는 취소된 원거래(reversed=true)와 그 반대기표(REVERSAL)를 집계에서 제외한다")
    void summarize_excludesReversedOriginalAndReversalEntries() {
        LedgerEntryJpaEntity original = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(ACCOUNT_ID)
                .transactionNumber("20260720WB0000000001")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(7_000L)
                .balanceAfter(1_000_000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent("취소된거래")
                .channel(TransferChannel.WB)
                .reversed(true)
                .occurredAt(LocalDateTime.of(2026, 7, 20, 9, 0, 0))
                .build();
        LedgerEntryJpaEntity savedOriginal = ledgerEntryJpaRepository.save(original);

        LedgerEntryJpaEntity reversal = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(ACCOUNT_ID)
                .transactionNumber("20260720WB0000000002")
                .direction(LedgerDirection.DEPOSIT)
                .amount(7_000L)
                .balanceAfter(1_007_000L)
                .transactionType("REVERSAL")
                .transactionContent("반대기표")
                .channel(TransferChannel.WB)
                .reversed(false)
                .reversalId(savedOriginal.getLedgerEntryId())
                .occurredAt(LocalDateTime.of(2026, 7, 20, 9, 0, 1))
                .build();
        ledgerEntryJpaRepository.save(reversal);
        entityManager.flush();
        entityManager.clear();

        LedgerHistoryQuery query = julyQuery(LedgerHistoryDirection.ALL, null, LedgerHistorySort.LATEST, 0, 10);

        LedgerHistoryAggregate aggregate = adapter.summarize(query);

        // setUp()의 7/15 입금 2만, 7/10 출금 1만만 집계되고, 취소된 7천원 쌍은 제외되어야 한다.
        assertThat(aggregate.depositCount()).isEqualTo(1);
        assertThat(aggregate.depositAmount()).isEqualTo(20_000L);
        assertThat(aggregate.withdrawalCount()).isEqualTo(1);
        assertThat(aggregate.withdrawalAmount()).isEqualTo(10_000L);
    }

    private LedgerHistoryQuery julyQuery(LedgerHistoryDirection direction, String keyword,
            LedgerHistorySort sort, int page, int size) {
        return LedgerHistoryQuery.builder()
                .accountId(ACCOUNT_ID)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 7, 31))
                .direction(direction)
                .keyword(keyword)
                .sort(sort)
                .page(page)
                .size(size)
                .build();
    }
}
