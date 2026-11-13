package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        verify(scores, never()).find(1L);
        verify(reads, never()).findLatestCallRecord(1L);
        verify(reads, never()).findLatestAction(1L);
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

    @Test
    void maximumPageNumberCannotOverflowOffset() {
        var page = service.find(Integer.MAX_VALUE, 100, null, null, null, null, 7L, "ip", AccessReason.OPERATION);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void sortsByHeldAtAndHoldIdDescending() {
        var page = service.find(0, 20, null, null, null, null, 7L, "ip", AccessReason.OPERATION);

        assertThat(page.items()).extracting("transferHoldId").containsExactly(1L, 2L);
    }

    @Test
    void missingOptionalDataUsesSafeDefaultsAndCallPath() {
        when(holds.findForFds(2L)).thenReturn(hold(2L, "HELD", "B", "12", NOW));
        when(jobs.findByHoldId(2L)).thenReturn(null);
        when(scores.find(2L)).thenReturn(null);
        when(risks.check("088", "12")).thenReturn(new FdsRiskAccountService.Check("NO_MATCH", null, "v1", null));
        when(reads.findLatestAction(2L)).thenReturn(null);

        var page = service.find(0, 20, "HELD", "B", "NO_MATCH", null, 7L, "ip", AccessReason.OPERATION);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.investigationStatus()).isNull();
            assertThat(item.behaviorScoreStatus()).isEqualTo("PENDING");
            assertThat(item.recipientAccountMasked()).isEqualTo("****");
            assertThat(item.availablePath()).isEqualTo("CALL_THEN_DECIDE");
        });
    }

    @Test
    void pendingActionRequiresRecoveryAndEqualTimesUseHoldIdOrder() {
        when(holds.findForFds(2L)).thenReturn(hold(2L, "HELD", "B", "110039200002", NOW));
        when(holds.findForFds(1L)).thenReturn(hold(1L, "HELD", "A", "110039200001", NOW));
        when(reads.findLatestAction(2L)).thenReturn(action("PENDING"));

        var page = service.find(0, 20, "HELD", null, null, null, 7L, "ip", AccessReason.OPERATION);

        assertThat(page.items()).extracting("transferHoldId").containsExactly(2L, 1L);
        assertThat(page.items().getFirst().availablePath()).isEqualTo("RECOVERY_REQUIRED");
    }

    @Test
    void blankFiltersAreIgnoredAndNullAccountIsSafelyMasked() {
        when(holds.findForFds(2L)).thenReturn(hold(2L, "HELD", "B", null, NOW));
        when(risks.check("088", null)).thenReturn(new FdsRiskAccountService.Check("UNAVAILABLE", null, null, "EMPTY"));

        var page = service.find(0, 20, " ", " ", " ", " ", 7L, "ip", AccessReason.OPERATION);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().getFirst().recipientAccountMasked()).isEqualTo("****");
    }

    private void stub(
            long id, String holdStatus, String policy, String risk, String investigation, String call, String action) {
        lenient()
                .when(holds.findForFds(id))
                .thenReturn(hold(id, holdStatus, policy, "11003920000" + id, NOW.minusMinutes(id)));
        lenient()
                .when(jobs.findByHoldId(id))
                .thenReturn(new FdsInvestigationJob(id, "inv-" + id, investigation, 1, NOW, null, null));
        lenient().when(scores.find(id)).thenReturn(new BehaviorScoreView(id, "AVAILABLE", 0.8, "m", "f", null));
        lenient()
                .when(risks.check("088", "11003920000" + id))
                .thenReturn(new FdsRiskAccountService.Check(risk, "MATCH".equals(risk) ? 1L : null, "v1", null));
        lenient()
                .when(reads.findLatestCallRecord(id))
                .thenReturn(call == null ? null : new FdsAdminReadPort.CallRecord(1L, call, 7L, "확인", NOW, true));
        lenient().when(reads.findLatestAction(id)).thenReturn(action == null ? null : action(action));
    }

    private static TransferHoldForFds hold(
            long id, String status, String policy, String accountNumber, LocalDateTime heldAt) {
        return new TransferHoldForFds(
                id,
                20L,
                null,
                status,
                heldAt,
                500_000L,
                accountNumber,
                policy,
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
