package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsInvestigationReportServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);

    @Mock
    FdsInvestigationJobPort jobs;

    private FdsInvestigationReportService service;

    @BeforeEach
    void setUp() {
        service = new FdsInvestigationReportService(jobs, CLOCK);
    }

    @Test
    void callbackAlonePersistsReportAndCompletesJob() {
        stubRunningJobWithEvidence();
        var result = service.submit("inv-123", report("[101]"));
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(jobs)
                .completeWithReport(
                        eq(123L),
                        eq("inv-123"),
                        eq(1),
                        eq("p6-investigation-v1"),
                        any(),
                        any(),
                        eq(LocalDateTime.now(CLOCK)));
    }

    @Test
    void rejectsEvidenceNeverProvidedByTool() {
        stubRunningJobWithEvidence();
        assertThatThrownBy(() -> service.submit("inv-123", report("[999]"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMalformedAndOversizedReports() {
        stubRunningJob();
        assertThatThrownBy(() -> service.submit("inv-123", "{}")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.submit("inv-123", "가".repeat(20_000))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMissingOrUnsupportedRecommendedAction() {
        stubRunningJob();
        String valid = report("[101]");

        assertThatThrownBy(() -> service.submit(
                        "inv-123", valid.replace(",\"recommendedAction\":\"MANUAL_CUSTOMER_CONFIRMATION\"", "")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () -> service.submit("inv-123", valid.replace("MANUAL_CUSTOMER_CONFIRMATION", "AUTO_APPROVE")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void evidenceLookupInfrastructureFailureIsNotConvertedToInvalidReport() {
        stubRunningJob();
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(jobs.findEvidenceIds("inv-123")).thenThrow(failure);

        assertThatThrownBy(() -> service.submit("inv-123", report("[101]"))).isSameAs(failure);
    }

    private static String report(String evidence) {
        return "{\"transferHoldId\":123,\"status\":\"COMPLETED\",\"secondaryFindings\":[{"
                + "\"code\":\"REGULAR_SPLIT_AMOUNTS\",\"description\":\"반복\","
                + "\"evidenceTransferIds\":" + evidence + ",\"confidence\":\"OBSERVED\"}],"
                + "\"missingInformation\":[],\"recommendedAction\":\"MANUAL_CUSTOMER_CONFIRMATION\","
                + "\"suggestedQuestions\":[],\"summary\":\"확인 필요\"}";
    }

    private void stubRunningJob() {
        when(jobs.findByInvestigationId("inv-123"))
                .thenReturn(
                        new FdsInvestigationJob(123L, "inv-123", "RUNNING", 1, LocalDateTime.now(CLOCK), null, null));
    }

    private void stubRunningJobWithEvidence() {
        stubRunningJob();
        when(jobs.findEvidenceIds("inv-123")).thenReturn(Set.of(101L));
    }
}
