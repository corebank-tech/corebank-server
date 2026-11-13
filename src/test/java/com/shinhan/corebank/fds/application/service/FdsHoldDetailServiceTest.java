package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class FdsHoldDetailServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Mock
    TransferHoldUseCase holds;

    @Mock
    BehaviorScorePort scores;

    @Mock
    AuditLogService audit;

    @Mock
    FdsRiskAccountService risks;

    @Mock
    FdsInvestigationJobPort jobs;

    @Mock
    FdsAdminReadPort reads;

    @Mock
    FdsInvestigationInputService inputs;

    private FdsHoldDetailService service;

    @BeforeEach
    void setUp() {
        service = new FdsHoldDetailService(holds, scores, audit, risks, jobs, reads, inputs);
        when(holds.findForFds(10L)).thenReturn(hold("HELD", null, null));
        when(scores.find(10L)).thenReturn(new BehaviorScoreView(10L, "AVAILABLE", 0.8, "m1", "f1", null));
        when(risks.check("088", "110039200000"))
                .thenReturn(new FdsRiskAccountService.Check("NO_MATCH", null, "risk-v1", null));
        when(jobs.findByHoldId(10L))
                .thenReturn(new FdsInvestigationJob(10L, "inv-10", "COMPLETED", 1, NOW, null, null));
        lenient()
                .when(inputs.behaviorSummary(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FdsInvestigationInput.BehaviorSummary(1, 2, 71, "AVAILABLE", 0.8, "m1", "f1"));
        when(reads.findToolResults("inv-10"))
                .thenReturn(new FdsAdminReadPort.ToolResults(
                        new FdsAdminReadPort.History(
                                NOW.minusHours(24),
                                NOW,
                                false,
                                NOW,
                                List.of(
                                        new FdsAdminReadPort.HistoryItem(
                                                101L, 500_000L, NOW.minusMinutes(1), "***-**-0000"),
                                        new FdsAdminReadPort.HistoryItem(
                                                102L, 400_000L, NOW.minusMinutes(2), "***-**-0001"))),
                        new FdsAdminReadPort.Recipient(NOW.minusDays(1), true, 2, NOW)));
        lenient()
                .when(reads.findCallRecords(10L))
                .thenReturn(List.of(new FdsAdminReadPort.CallRecord(1L, "CUSTOMER_CONFIRMED", 7L, "본인 확인", NOW, true)));
        lenient()
                .when(reads.findLatestAction(10L))
                .thenReturn(new FdsAdminReadPort.AdminAction(
                        "action",
                        "APPROVE",
                        "PENDING",
                        7L,
                        null,
                        1L,
                        "NO_MATCH",
                        null,
                        "risk-v1",
                        null,
                        null,
                        null,
                        null,
                        NOW,
                        NOW));
    }

    @Test
    void assemblesStoredReportToolCallAndActionWithoutExternalRequery() {
        when(reads.findReport("inv-10"))
                .thenReturn(new FdsAdminReadPort.Report(
                        "p6-investigation-v1",
                        "{\"status\":\"COMPLETED\",\"secondaryFindings\":[{\"code\":\"REGULAR_SPLIT_AMOUNTS\","
                                + "\"description\":\"반복\",\"confidence\":\"OBSERVED\",\"evidenceTransferIds\":[101]}],"
                                + "\"missingInformation\":[],\"suggestedQuestions\":[\"목적 확인\"],\"summary\":\"확인 필요\"}",
                        NOW));

        var detail = service.get(10L, 7L, "127.0.0.1", AccessReason.OPERATION);

        assertThat(detail.agentReport().secondaryFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("REGULAR_SPLIT_AMOUNTS");
            assertThat(finding.evidenceTransferIds()).containsExactly(101L);
        });
        assertThat(detail.toolResults().history().items().getFirst().usedAsEvidence())
                .isTrue();
        assertThat(detail.toolResults().history().items().get(1).usedAsEvidence())
                .isFalse();
        assertThat(detail.toolResults().recipientContext().executed()).isTrue();
        assertThat(detail.callRecords())
                .singleElement()
                .extracting("selectedByAction")
                .isEqualTo(true);
        assertThat(detail.adminAction().recoveryRequired()).isTrue();
        assertThat(detail.originalTransfer().recipientAccountMasked()).doesNotContain("110039200000");
    }

    @Test
    void keepsLegacyTopLevelFieldsInSerializedResponse() throws Exception {
        when(reads.findReport("inv-10"))
                .thenReturn(new FdsAdminReadPort.Report(
                        "p6-investigation-v1",
                        "{\"status\":\"COMPLETED\",\"secondaryFindings\":[],\"missingInformation\":[],"
                                + "\"suggestedQuestions\":[],\"summary\":\"확인 필요\"}",
                        NOW));

        String json = new ObjectMapper().writeValueAsString(service.get(10L, 7L, "127.0.0.1", AccessReason.OPERATION));

        assertThat(json)
                .contains("\"transferHoldId\":10")
                .contains("\"amount\":500000")
                .contains("\"holdStatus\":\"HELD\"")
                .contains("\"recipientAccountMasked\"")
                .contains("\"investigationStatus\":\"COMPLETED\"")
                .contains("\"behaviorScoreStatus\":\"AVAILABLE\"")
                .contains("\"behaviorRiskScore\":0.8")
                .contains("\"modelVersion\":\"m1\"")
                .contains("\"featureVersion\":\"f1\"")
                .contains("\"behaviorScoreUnavailableReason\":null")
                .contains("\"secondaryFindings\":[]")
                .contains("\"originalTransfer\"")
                .contains("\"behaviorScore\"");
    }

    @Test
    void missingSnapshotsAreExplicitAndCancelledCustomerIsNotShownAsAdmin() {
        when(holds.findForFds(10L)).thenReturn(hold("CANCELLED", 20L, null));
        when(reads.findToolResults("inv-10")).thenReturn(null);
        when(reads.findReport("inv-10")).thenReturn(null);

        var detail = service.get(10L, 7L, "127.0.0.1", AccessReason.OPERATION);

        assertThat(detail.toolResults().history().executed()).isFalse();
        assertThat(detail.toolResults().recipientContext().executed()).isFalse();
        assertThat(detail.agentReport()).isNull();
        assertThat(detail.originalTransfer().processedActorId()).isNull();
    }

    @Test
    void invalidStoredReportIsRejectedWithoutLeakingParserFailure() {
        when(reads.findReport("inv-10")).thenReturn(new FdsAdminReadPort.Report("v1", "{", NOW));
        assertThatThrownBy(() -> service.get(10L, 7L, "127.0.0.1", AccessReason.OPERATION))
                .isInstanceOf(BusinessException.class);
    }

    private static TransferHoldForFds hold(String status, Long actorId, String reason) {
        return new TransferHoldForFds(
                10L,
                20L,
                null,
                status,
                NOW,
                500_000L,
                "110039200000",
                "A",
                30L,
                "088",
                "ON_APPROVAL",
                actorId,
                reason,
                "HELD".equals(status) ? null : NOW,
                3,
                1_500_000L,
                true,
                true);
    }
}
