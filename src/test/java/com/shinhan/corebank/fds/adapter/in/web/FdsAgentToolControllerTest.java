package com.shinhan.corebank.fds.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsAgentTokenVerifier;
import com.shinhan.corebank.fds.application.service.FdsAgentToolService;
import com.shinhan.corebank.fds.application.service.FdsInvestigationReportService;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdsAgentToolControllerTest {
    @Test
    void exposesToolsAndReportOnlyWithDedicatedTokenAndMatchingKey() {
        FdsAgentTokenVerifier tokens = new FdsAgentTokenVerifier("agent-secret");
        FdsAgentToolService tools = mock(FdsAgentToolService.class);
        FdsInvestigationReportService reports = mock(FdsInvestigationReportService.class);
        var history = new FdsAgentToolService.HistoryData(10L, List.of(), false);
        var recipient = new FdsAgentToolService.RecipientData(10L, false, null, 0);
        var acknowledgement = new FdsInvestigationReportService.Acknowledgement("inv-1", "COMPLETED");
        when(tools.history(10L)).thenReturn(history);
        when(tools.recipient(10L)).thenReturn(recipient);
        when(reports.submit("inv-1", "{}")).thenReturn(acknowledgement);
        FdsAgentToolController controller = new FdsAgentToolController(tokens, tools, reports);

        assertThat(controller.history(10L, "Bearer agent-secret").data()).isSameAs(history);
        assertThat(controller.recipient(10L, "Bearer agent-secret").data()).isSameAs(recipient);
        assertThat(controller
                        .report("inv-1", "Bearer agent-secret", "inv-1", "{}")
                        .data())
                .isSameAs(acknowledgement);
        assertThatThrownBy(() -> controller.report("inv-1", "Bearer agent-secret", "other", "{}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.history(10L, "Bearer score-secret")).isInstanceOf(BusinessException.class);
    }
}
