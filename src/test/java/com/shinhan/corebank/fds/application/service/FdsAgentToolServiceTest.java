package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import com.shinhan.corebank.transfer.api.SucceededTransferHistoryQuery;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsAgentToolServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime HELD_AT = LocalDateTime.now(CLOCK);

    @Mock
    FdsInvestigationJobPort jobs;

    @Mock
    TransferHoldUseCase holds;

    @Mock
    SucceededTransferHistoryQuery history;

    private FdsAgentToolService service;

    @BeforeEach
    void setUp() {
        service = new FdsAgentToolService(jobs, holds, history, CLOCK);
    }

    @Test
    void historyLimitsToOneHundredMasksAccountsAndRecordsProvidedEvidence() {
        stubContext("RUNNING");
        List<SucceededTransfer> transfers = LongStream.rangeClosed(1, 101)
                .mapToObj(id -> new SucceededTransfer(id, id * 1000, HELD_AT.minusMinutes(id), "110039200000"))
                .toList();
        when(history.findRecent(20L, HELD_AT.minusHours(24), HELD_AT, 101)).thenReturn(transfers);

        var result = service.history(10L, 1);

        assertThat(result.hasMore()).isTrue();
        assertThat(result.items()).hasSize(100);
        assertThat(result.items().getFirst().recipientAccountMasked()).isEqualTo("***-**-0000");
        verify(jobs)
                .recordHistorySnapshot(
                        eq("inv-10"),
                        eq(10L),
                        eq(1),
                        eq(20L),
                        eq(HELD_AT.minusHours(24)),
                        eq(HELD_AT),
                        eq(true),
                        argThat(items -> items.size() == 100
                                && items.getFirst().refId() == 1L
                                && "***-**-0000".equals(items.getFirst().recipientAccountMasked())),
                        eq(HELD_AT));
    }

    @Test
    void recipientContextUsesHoldOwnedScopeAndWindowStart() {
        stubContext("RUNNING");
        when(history.summarizeRecipient(20L, "110039200000", 30L))
                .thenReturn(new RecipientHistory(HELD_AT.minusMinutes(11), 2));

        var result = service.recipient(10L, 1);

        assertThat(result.hasSucceededBeforeWindow()).isTrue();
        assertThat(result.otherWithdrawalAccountCount()).isEqualTo(2);
        verify(jobs).recordRecipientSnapshot("inv-10", 10L, 1, 20L, HELD_AT.minusMinutes(11), true, 2, HELD_AT);
    }

    @Test
    void rejectsMissingOrInactiveInvestigation() {
        when(jobs.findByHoldId(10L)).thenReturn(null);
        assertThatThrownBy(() -> service.history(10L, 1)).isInstanceOf(BusinessException.class);

        when(jobs.findByHoldId(10L)).thenReturn(job("FAILED"));
        assertThatThrownBy(() -> service.recipient(10L, 1)).isInstanceOf(BusinessException.class);

        when(jobs.findByHoldId(10L)).thenReturn(job("RUNNING"));
        assertThatThrownBy(() -> service.history(10L, 2)).isInstanceOf(BusinessException.class);
    }

    @Test
    void masksUnavailableShortAccountAndHandlesNoPriorRecipient() {
        stubContext("RUNNING", "123");
        when(history.findRecent(eq(20L), any(), eq(HELD_AT), eq(101)))
                .thenReturn(List.of(new SucceededTransfer(1L, 1000L, HELD_AT.minusMinutes(1), null)));
        assertThat(service.history(10L, 1).items().getFirst().recipientAccountMasked())
                .isEqualTo("****");

        when(history.summarizeRecipient(20L, "123", 30L)).thenReturn(new RecipientHistory(null, 0));
        assertThat(service.recipient(10L, 1).hasSucceededBeforeWindow()).isFalse();
    }

    private void stubContext(String status) {
        stubContext(status, "110039200000");
    }

    private void stubContext(String status, String account) {
        when(jobs.findByHoldId(10L)).thenReturn(job(status));
        when(holds.findForFds(10L)).thenReturn(hold(account));
    }

    private static FdsInvestigationJob job(String status) {
        return new FdsInvestigationJob(10L, "inv-10", status, 1, HELD_AT, null, null);
    }

    private static TransferHoldForFds hold(String account) {
        return new TransferHoldForFds(
                10L,
                20L,
                null,
                "HELD",
                HELD_AT,
                1000L,
                account,
                "A",
                30L,
                "088",
                "ON_APPROVAL",
                null,
                null,
                null,
                3,
                1_500_000L,
                true,
                true);
    }
}
