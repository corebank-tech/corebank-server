package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsHoldListServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 12, 0);

    @Mock
    FdsAdminReadPort reads;

    @Mock
    TransferHoldUseCase holds;

    @Mock
    FdsInvestigationJobPort jobs;

    @Mock
    BehaviorScorePort scores;

    @Mock
    FdsRiskAccountService risks;

    @Mock
    AuditLogService audit;

    private FdsHoldListService service;

    @BeforeEach
    void setUp() {
        service = new FdsHoldListService(reads, holds, jobs, scores, risks, audit);
        when(reads.findHoldIds()).thenReturn(List.of(2L, 1L));
        stub(2L, "HELD", "B", "MATCH", "RUNNING", null, null);
        stub(1L, "REJECTED", "A", "NO_MATCH", "COMPLETED", "CUSTOMER_DENIED", "COMPLETED");
    }

    @Test
    void filtersAndPagesLatestInvestigationCases() {
        var page = service.find(0, 1, "HELD", "B", "MATCH", "RUNNING", 7L, "127.0.0.1", AccessReason.OPERATION);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.transferHoldId()).isEqualTo(2L);
            assertThat(item.availablePath()).isEqualTo("MATCH_REJECT_ONLY");
            assertThat(item.recipientAccountMasked()).isEqualTo("***-**-0002");
        });
        verify(audit)
                .recordPersonalDataAccess(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(PersonalDataAccessEventType.FDS_HOLD_LIST_VIEW),
                        org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                        org.mockito.ArgumentMatchers.eq(true),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.eq(AccessReason.OPERATION));
    }

    @Test
    void completedAndRecoveryPathsAreDistinguished() {
        var completed = service.find(0, 20, null, "A", null, null, 7L, "ip", AccessReason.OPERATION);
        assertThat(completed.items().getFirst().availablePath()).isEqualTo("COMPLETED");

        when(reads.findLatestAction(2L)).thenReturn(action("CONFLICT"));
        var recovery = service.find(0, 20, "HELD", null, null, null, 7L, "ip", AccessReason.OPERATION);
        assertThat(recovery.items().getFirst().availablePath()).isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    void emptyPageKeepsStableMetadata() {
        var page = service.find(3, 20, "EXPIRED", null, null, null, 7L, "ip", AccessReason.OPERATION);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    private void stub(
            long id, String holdStatus, String policy, String risk, String investigation, String call, String action) {
        when(holds.findForFds(id))
                .thenReturn(new TransferHoldForFds(
                        id,
                        20L,
                        null,
                        holdStatus,
                        NOW.minusMinutes(id),
                        500_000L,
                        "11003920000" + id,
                        policy,
                        3,
                        1_500_000L,
                        true,
                        true,
                        30L,
                        "088",
                        "ON_APPROVAL",
                        null,
                        null,
                        null));
        when(jobs.findByHoldId(id))
                .thenReturn(new FdsInvestigationJob(id, "inv-" + id, investigation, 1, NOW, null, null));
        when(scores.find(id)).thenReturn(new BehaviorScoreView(id, "AVAILABLE", 0.8, "m", "f", null));
        when(risks.check("088", "11003920000" + id))
                .thenReturn(new FdsRiskAccountService.Check(risk, "MATCH".equals(risk) ? 1L : null, "v1", null));
        when(reads.findCallRecords(id))
                .thenReturn(
                        call == null
                                ? List.of()
                                : List.of(new FdsAdminReadPort.CallRecord(1L, call, 7L, "확인", NOW, true)));
        when(reads.findLatestAction(id)).thenReturn(action == null ? null : action(action));
    }

    private static FdsAdminReadPort.AdminAction action(String status) {
        return new FdsAdminReadPort.AdminAction(
                "action",
                "REJECT",
                status,
                7L,
                "CUSTOMER_DENIED",
                1L,
                "NO_MATCH",
                null,
                "v1",
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }
}
