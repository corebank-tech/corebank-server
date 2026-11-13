package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsInvestigationJobServiceTest {
    private static final String INVESTIGATION_ID = "00000000-0000-0000-0000-000000000992";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    FdsInvestigationJobPort jobs;

    @Mock
    FdsInvestigationInputService inputs;

    @Mock
    TransferHoldUseCase holds;

    @Mock
    FdsInvestigationAgentPort agent;

    private FdsInvestigationJobService service;

    @BeforeEach
    void setUp() {
        service = new FdsInvestigationJobService(jobs, inputs, holds, CLOCK);
        lenient().when(jobs.findDue(any(), anyInt())).thenReturn(List.of(92L));
        lenient().when(jobs.claim(anyLong(), any(), any())).thenReturn(1);
        lenient().when(jobs.findByHoldId(92L)).thenReturn(job("RUNNING"));
        lenient().when(holds.findForFds(92L)).thenReturn(hold("HELD"));
        lenient().when(inputs.build(92L, INVESTIGATION_ID)).thenReturn(input());
    }

    @Test
    void workerOnlyConfirmsReportAlreadyCompletedByCallback() {
        when(agent.investigate(any())).thenAnswer(invocation -> {
            when(jobs.findByHoldId(92L)).thenReturn(job("COMPLETED"));
            when(jobs.hasReport(INVESTIGATION_ID)).thenReturn(true);
            return new FdsInvestigationAgentPort.Result(INVESTIGATION_ID, 92L, "COMPLETED");
        });

        service.runDue(agent, 20);

        verify(jobs, never()).completeWithReport(anyLong(), any(), anyInt(), any(), any(), any(), any());
        verify(jobs, never()).retry(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void completedAckWithoutCallbackReportIsRetried() {
        when(agent.investigate(any()))
                .thenReturn(new FdsInvestigationAgentPort.Result(INVESTIGATION_ID, 92L, "COMPLETED"));

        service.runDue(agent, 20);

        verify(jobs)
                .retry(
                        92L,
                        1,
                        LocalDateTime.now(CLOCK).plusMinutes(1),
                        "AGENT_ACK_WITHOUT_REPORT",
                        LocalDateTime.now(CLOCK));
    }

    @Test
    void invalidAcknowledgementFailsWithoutReportWrite() {
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("other", 92L, "COMPLETED"));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, 1, "INVALID_AGENT_ACK", LocalDateTime.now(CLOCK));
        verify(jobs, never()).completeWithReport(anyLong(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void transientAgentFailureUsesStableInvestigationIdAndRetries() {
        when(agent.investigate(any())).thenThrow(new FdsInvestigationAgentPort.Failure(true));

        service.runDue(agent, 20);

        verify(inputs).build(92L, INVESTIGATION_ID);
        verify(jobs)
                .retry(92L, 1, LocalDateTime.now(CLOCK).plusMinutes(1), "AGENT_UNAVAILABLE", LocalDateTime.now(CLOCK));
    }

    @Test
    void permanentAgentFailureFinalizesFailure() {
        when(agent.investigate(any())).thenThrow(new FdsInvestigationAgentPort.Failure(false));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, 1, "AGENT_PERMANENT_FAILURE", LocalDateTime.now(CLOCK));
    }

    @Test
    void staleClaimGenerationCannotDriveLaterStateChanges() {
        when(jobs.claim(anyLong(), any(), any())).thenReturn(2);
        when(jobs.findByHoldId(92L)).thenReturn(job("RUNNING"));

        service.runDue(agent, 20);

        verify(agent, never()).investigate(any());
        verify(jobs, never()).retry(anyLong(), anyInt(), any(), any(), any());
        verify(jobs, never()).fail(anyLong(), anyInt(), any(), any());
    }

    @Test
    void skippedClaimDoesNotReadOrRunJob() {
        when(jobs.claim(anyLong(), any(), any())).thenReturn(null);

        service.runDue(agent, 20);

        verify(jobs, never()).findByHoldId(92L);
        verify(agent, never()).investigate(any());
    }

    @Test
    void closedHoldFailsOnlyCurrentAttempt() {
        when(holds.findForFds(92L)).thenReturn(hold("CANCELLED"));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, 1, "HOLD_CLOSED", LocalDateTime.now(CLOCK));
        verify(agent, never()).investigate(any());
    }

    @Test
    void thirdTransientFailureBecomesFinalFailure() {
        when(jobs.claim(anyLong(), any(), any())).thenReturn(3);
        when(jobs.findByHoldId(92L)).thenReturn(job("RUNNING", 3));
        when(agent.investigate(any())).thenThrow(new IllegalStateException("network"));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, 3, "AGENT_UNAVAILABLE", LocalDateTime.now(CLOCK));
        verify(jobs, never()).retry(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void registerAndRecoverDelegateWithCurrentTime() {
        var event = new com.shinhan.corebank.transfer.api.TransferHeld(
                92L,
                7L,
                "A",
                com.shinhan.corebank.transfer.api.TransferHoldReleaseMode.ON_APPROVAL,
                null,
                null,
                LocalDateTime.now(CLOCK));
        when(jobs.createIfAbsent(eq(92L), any(), eq(LocalDateTime.now(CLOCK)))).thenReturn(job("PENDING"));
        when(jobs.recoverExpired(LocalDateTime.now(CLOCK))).thenReturn(2);

        service.register(event);

        assertThat(service.recoverExpired()).isEqualTo(2);
        verify(jobs).createIfAbsent(eq(92L), any(), eq(LocalDateTime.now(CLOCK)));
    }

    private static FdsInvestigationJob job(String status) {
        return job(status, 1);
    }

    private static FdsInvestigationJob job(String status, int attemptCount) {
        return new FdsInvestigationJob(
                92L,
                INVESTIGATION_ID,
                status,
                attemptCount,
                LocalDateTime.now(CLOCK),
                LocalDateTime.now(CLOCK).plusSeconds(70),
                null);
    }

    private static TransferHoldForFds hold(String status) {
        return new TransferHoldForFds(
                92L,
                7L,
                null,
                status,
                LocalDateTime.now(CLOCK),
                1_000_000L,
                "110123456789",
                "A",
                101L,
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

    private static FdsInvestigationInput input() {
        return new FdsInvestigationInput(
                INVESTIGATION_ID,
                92L,
                "A",
                new FdsInvestigationInput.PolicyEvidence(3, 1_500_000L),
                new FdsInvestigationInput.BehaviorSummary(1, 2, 71, "PENDING", null, null, null),
                new FdsInvestigationInput.TransferSummary(1_000_000L, "***-**-6789"));
    }
}
