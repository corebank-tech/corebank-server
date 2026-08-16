package com.shinhan.corebank.transfer.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryQueryPort;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerHistoryQueryServiceTest {

    @Mock
    private LedgerHistoryQueryPort ledgerHistoryQueryPort;

    private LedgerHistoryQueryService service;

    @Test
    @DisplayName("포트가 돌려준 LedgerEntry 목록을 LedgerHistoryItem으로 변환하고, summary·totalPages를 함께 채운다")
    void query_mapsEntriesAndComputesTotalPages() {
        service = new LedgerHistoryQueryService(ledgerHistoryQueryPort);

        LedgerHistoryQuery query = LedgerHistoryQuery.builder()
                .accountId(101L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 7, 31))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(10)
                .build();

        LedgerEntry entry = LedgerEntry.builder()
                .ledgerEntryId(1L)
                .transactionNumber("20260715WB0000000001")
                .occurredAt(LocalDateTime.of(2026, 7, 15, 10, 0, 0))
                .transactionType("IMMEDIATE_TRANSFER")
                .direction(LedgerDirection.DEPOSIT)
                .amount(20_000L)
                .transactionContent("용돈")
                .balanceAfter(120_000L)
                .channel(TransferChannel.WB)
                .build();

        when(ledgerHistoryQueryPort.findEntries(query)).thenReturn(List.of(entry));
        when(ledgerHistoryQueryPort.countEntries(query)).thenReturn(25L);
        when(ledgerHistoryQueryPort.summarize(query))
                .thenReturn(new LedgerHistoryAggregate(1L, 20_000L, 0L, 0L));

        LedgerHistoryResult result = service.query(query);

        assertThat(result.items()).hasSize(1);
        LedgerHistoryItem item = result.items().get(0);
        assertThat(item.ledgerEntryId()).isEqualTo(1L);
        assertThat(item.transactionNumber()).isEqualTo("20260715WB0000000001");
        assertThat(item.direction()).isEqualTo(LedgerDirection.DEPOSIT);
        assertThat(item.amount()).isEqualTo(20_000L);

        assertThat(result.totalCount()).isEqualTo(25L);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10)
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);

        assertThat(result.summary().depositCount()).isEqualTo(1L);
        assertThat(result.summary().depositAmount()).isEqualTo(20_000L);
        assertThat(result.summary().withdrawalCount()).isZero();
    }

    @Test
    @DisplayName("일치하는 거래가 없으면 items는 빈 리스트, totalPages는 0이다")
    void query_noMatches_returnsEmptyItemsAndZeroTotalPages() {
        service = new LedgerHistoryQueryService(ledgerHistoryQueryPort);

        LedgerHistoryQuery query = LedgerHistoryQuery.builder()
                .accountId(101L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 7, 31))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(10)
                .build();

        when(ledgerHistoryQueryPort.findEntries(query)).thenReturn(List.of());
        when(ledgerHistoryQueryPort.countEntries(query)).thenReturn(0L);
        when(ledgerHistoryQueryPort.summarize(query)).thenReturn(LedgerHistoryAggregate.empty());

        LedgerHistoryResult result = service.query(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
