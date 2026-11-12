package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FdsInvestigationJobServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final FdsInvestigationJobPort jobs = mock(FdsInvestigationJobPort.class);
    private final FdsInvestigationInputService inputs = mock(FdsInvestigationInputService.class);
    private final TransferHoldUseCase holds = mock(TransferHoldUseCase.class);
    private final FdsInvestigationAgentPort agent = mock(FdsInvestigationAgentPort.class);
    private final FdsInvestigationJobService service = new FdsInvestigationJobService(jobs, inputs, holds, CLOCK);

    @BeforeEach
    void setUp() {
        when(jobs.findDue(any(), anyInt())).thenReturn(List.of(92L));
        when(jobs.claim(anyLong(), any(), any())).thenReturn(true);
        when(jobs.findByHoldId(92L)).thenReturn(job(1));
        when(jobs.findEvidenceIds(anyString())).thenReturn(Set.of());
        when(holds.findForFds(92L)).thenReturn(hold("HELD"));
        when(inputs.build(92L, "00000000-0000-0000-0000-000000000992")).thenReturn(mock(FdsInvestigationInput.class));
    }

    @Test
    void registersOnlyDurableJobAndDoesNotCallAgent() {
        TransferHeld event = new TransferHeld(
                92L, 7L, "A", TransferHoldReleaseMode.ON_APPROVAL, null, null, LocalDateTime.now(CLOCK));
        when(jobs.createIfAbsent(anyLong(), anyString(), any())).thenReturn(job(0));

        assertThat(service.register(event).transferHoldId()).isEqualTo(92L);

        verify(jobs).createIfAbsent(anyLong(), anyString(), any());
        verify(agent, never()).investigate(any());
    }

    @Test
    void completesWithValidatedReportUsingSameInvestigationId() {
        String json =
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],"missingInformation":[],
                "suggestedQuestions":[]}
                """;
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("p6-investigation-v1", json));

        service.runDue(agent, 20);

        verify(jobs)
                .completeWithReport(
                        org.mockito.ArgumentMatchers.eq(92L),
                        org.mockito.ArgumentMatchers.eq("00000000-0000-0000-0000-000000000992"),
                        org.mockito.ArgumentMatchers.eq("p6-investigation-v1"),
                        org.mockito.ArgumentMatchers.eq(json),
                        anyString(),
                        any());
    }

    @Test
    void retriesAfterAgentFailureAndKeepsStableInvestigationId() {
        when(agent.investigate(any())).thenThrow(new IllegalStateException("connection failed"));
        ArgumentCaptor<LocalDateTime> nextAttempt = ArgumentCaptor.forClass(LocalDateTime.class);

        service.runDue(agent, 20);

        verify(jobs)
                .retry(
                        org.mockito.ArgumentMatchers.eq(92L),
                        nextAttempt.capture(),
                        org.mockito.ArgumentMatchers.eq("AGENT_UNAVAILABLE"),
                        any());
        assertThat(nextAttempt.getValue()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(1));
        verify(inputs).build(92L, "00000000-0000-0000-0000-000000000992");
    }

    @Test
    void closesJobWithoutAgentCallWhenHoldIsCancelled() {
        when(holds.findForFds(92L)).thenReturn(hold("CANCELLED"));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, "HOLD_CLOSED", LocalDateTime.now(CLOCK));
        verify(agent, never()).investigate(any());
    }

    @Test
    void rejectsEvidenceThatWasNotProvidedWithoutRetrying() {
        String json =
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[
                {"code":"REGULAR_SPLIT_AMOUNTS","description":"분할 이체","confidence":"OBSERVED",
                "evidenceTransferIds":[999]}],"missingInformation":[],"suggestedQuestions":[]}
                """;
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("p6-investigation-v1", json));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, "FDS0002", LocalDateTime.now(CLOCK));
        verify(jobs, never()).retry(anyLong(), any(), anyString(), any());
    }

    @Test
    void acceptsOnlyEvidenceRecordedForInvestigation() {
        String json =
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[
                {"code":"REGULAR_SPLIT_AMOUNTS","description":"분할 이체","confidence":"OBSERVED",
                "evidenceTransferIds":[101]}],"missingInformation":[],"suggestedQuestions":[]}
                """;
        when(jobs.findEvidenceIds("00000000-0000-0000-0000-000000000992")).thenReturn(Set.of(101L));
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("p6-investigation-v1", json));

        service.runDue(agent, 20);

        verify(jobs).completeWithReport(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNotStoreLateReportAfterHoldIsExecuted() {
        when(holds.findForFds(92L)).thenReturn(hold("HELD"), hold("EXECUTED"));
        when(agent.investigate(any()))
                .thenReturn(
                        new FdsInvestigationAgentPort.Result(
                                "p6-investigation-v1",
                                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],
                "missingInformation":[],"suggestedQuestions":[]}
                """));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, "HOLD_CLOSED", LocalDateTime.now(CLOCK));
        verify(jobs, never()).completeWithReport(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void thirdTransientFailureBecomesFailed() {
        when(jobs.findByHoldId(92L)).thenReturn(job(3));
        when(agent.investigate(any())).thenThrow(new IllegalStateException("timeout"));

        service.runDue(agent, 20);

        verify(jobs).fail(92L, "AGENT_UNAVAILABLE", LocalDateTime.now(CLOCK));
        verify(jobs, never()).retry(anyLong(), any(), anyString(), any());
    }

    @Test
    void secondTransientFailureRetriesAfterFiveMinutes() {
        when(jobs.findByHoldId(92L)).thenReturn(job(2));
        when(agent.investigate(any())).thenThrow(new IllegalStateException("timeout"));

        service.runDue(agent, 20);

        verify(jobs).retry(92L, LocalDateTime.now(CLOCK).plusMinutes(5), "AGENT_UNAVAILABLE", LocalDateTime.now(CLOCK));
    }

    @Test
    void doesNothingWhenAnotherWorkerAlreadyClaimedJob() {
        when(jobs.claim(anyLong(), any(), any())).thenReturn(false);

        service.runDue(agent, 20);

        verify(jobs, never()).findByHoldId(anyLong());
        verify(agent, never()).investigate(any());
    }

    @Test
    void doesNothingWhenClaimedJobDisappears() {
        when(jobs.findByHoldId(92L)).thenReturn(null);

        service.runDue(agent, 20);

        verify(holds, never()).findForFds(anyLong());
        verify(agent, never()).investigate(any());
    }

    @Test
    void retriesInfrastructureFailureWhenEvidenceLookupFails() {
        String json =
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],
                "missingInformation":[],"suggestedQuestions":[]}
                """;
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("p6-investigation-v1", json));
        when(jobs.findEvidenceIds(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        service.runDue(agent, 20);

        verify(jobs)
                .retry(
                        92L,
                        LocalDateTime.now(CLOCK).plusMinutes(1),
                        "INVESTIGATION_INFRA_FAILURE",
                        LocalDateTime.now(CLOCK));
        verify(jobs, never()).fail(92L, "FDS0002", LocalDateTime.now(CLOCK));
    }

    @Test
    void retriesBusinessFailureFromHoldLookupWithContractCode() {
        when(holds.findForFds(92L)).thenThrow(new BusinessException(FdsErrorCode.NOT_FOUND));

        service.runDue(agent, 20);

        verify(jobs)
                .retry(
                        92L,
                        LocalDateTime.now(CLOCK).plusMinutes(1),
                        "HOLD_OR_INPUT_" + FdsErrorCode.NOT_FOUND.getCode(),
                        LocalDateTime.now(CLOCK));
    }

    @Test
    void reportPersistenceConflictFailsClaimedJob() {
        String json =
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],
                "missingInformation":[],"suggestedQuestions":[]}
                """;
        when(agent.investigate(any())).thenReturn(new FdsInvestigationAgentPort.Result("p6-investigation-v1", json));
        org.mockito.Mockito.doThrow(new BusinessException(FdsErrorCode.FLOW_CONFLICT))
                .when(jobs)
                .completeWithReport(anyLong(), anyString(), anyString(), anyString(), anyString(), any());

        service.runDue(agent, 20);

        verify(jobs).fail(92L, FdsErrorCode.FLOW_CONFLICT.getCode(), LocalDateTime.now(CLOCK));
    }

    @Test
    void rejectsMalformedAndOutOfContractReports() {
        FdsInvestigationAgentPort.Result wrongIdentity = new FdsInvestigationAgentPort.Result(
                "p6-investigation-v1",
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"wrong","transferHoldId":92,
                "status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],"missingInformation":[],
                "suggestedQuestions":[]}
                """);
        FdsInvestigationAgentPort.Result malformed = new FdsInvestigationAgentPort.Result("p6-investigation-v1", "{");
        FdsInvestigationAgentPort.Result invalidStringArray = new FdsInvestigationAgentPort.Result(
                "p6-investigation-v1",
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[],
                "missingInformation":"없음","suggestedQuestions":[]}
                """);
        FdsInvestigationAgentPort.Result invalidEvidenceArray = new FdsInvestigationAgentPort.Result(
                "p6-investigation-v1",
                """
                {"schemaVersion":"p6-investigation-v1","investigationId":"00000000-0000-0000-0000-000000000992",
                "transferHoldId":92,"status":"COMPLETED","summary":"확인 필요","secondaryFindings":[
                {"code":"REGULAR_SPLIT_AMOUNTS","description":"분할 이체","confidence":"OBSERVED",
                "evidenceTransferIds":"101"}],"missingInformation":[],"suggestedQuestions":[]}
                """);
        when(agent.investigate(any()))
                .thenReturn(null, wrongIdentity, malformed, invalidStringArray, invalidEvidenceArray);

        for (int attempt = 0; attempt < 5; attempt++) service.runDue(agent, 20);

        verify(jobs, org.mockito.Mockito.times(5))
                .fail(92L, FdsErrorCode.INVALID_INVESTIGATION_REPORT.getCode(), LocalDateTime.now(CLOCK));
    }

    @Test
    void eachDueJobReceivesFreshLeaseStart() {
        StepClock stepClock = new StepClock();
        FdsInvestigationJobService stepService = new FdsInvestigationJobService(jobs, inputs, holds, stepClock);
        when(jobs.findDue(any(), anyInt())).thenReturn(List.of(92L, 93L));
        when(jobs.claim(anyLong(), any(), any())).thenReturn(false);
        ArgumentCaptor<LocalDateTime> starts = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> leases = ArgumentCaptor.forClass(LocalDateTime.class);

        stepService.runDue(agent, 20);

        verify(jobs, org.mockito.Mockito.times(2)).claim(anyLong(), starts.capture(), leases.capture());
        assertThat(starts.getAllValues().get(1)).isAfter(starts.getAllValues().get(0));
        assertThat(leases.getAllValues().get(0))
                .isEqualTo(starts.getAllValues().get(0).plusSeconds(70));
        assertThat(leases.getAllValues().get(1))
                .isEqualTo(starts.getAllValues().get(1).plusSeconds(70));
    }

    @Test
    void delegatesExpiredLeaseRecovery() {
        when(jobs.recoverExpired(LocalDateTime.now(CLOCK))).thenReturn(2);

        assertThat(service.recoverExpired()).isEqualTo(2);
    }

    private static FdsInvestigationJob job(int attempts) {
        return new FdsInvestigationJob(
                92L,
                "00000000-0000-0000-0000-000000000992",
                attempts == 0 ? "PENDING" : "RUNNING",
                attempts,
                LocalDateTime.now(CLOCK),
                attempts == 0 ? null : LocalDateTime.now(CLOCK).plusSeconds(70),
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
                3,
                1_500_000L,
                true,
                true);
    }

    private static final class StepClock extends Clock {
        private final AtomicInteger seconds = new AtomicInteger();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.parse("2026-11-12T03:00:00Z").plusSeconds(seconds.getAndIncrement());
        }
    }
}
