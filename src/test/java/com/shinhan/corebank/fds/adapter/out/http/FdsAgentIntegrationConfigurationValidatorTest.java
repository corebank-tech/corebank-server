package com.shinhan.corebank.fds.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FdsAgentIntegrationConfigurationValidatorTest {

    @Test
    void disabledFeaturesAllowMissingTokens() {
        assertThatCode(() -> validator(false, false, "", "", "")).doesNotThrowAnyException();
        assertThatCode(() -> validator(false, false, "unused", "unused", "unused"))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledScoreRequiresItsTokenWithoutExposingValues() {
        assertThatThrownBy(() -> validator(true, false, " ", "investigation-secret", "agent-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FDS_SCORE_SERVICE_TOKEN")
                .hasMessageNotContaining("investigation-secret")
                .hasMessageNotContaining("agent-secret");
    }

    @Test
    void enabledInvestigationRequiresOutboundAndCallbackTokens() {
        assertThatThrownBy(() -> validator(false, true, "score-secret", "", "agent-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FDS_INVESTIGATION_SERVICE_TOKEN");
        assertThatThrownBy(() -> validator(false, true, "score-secret", "investigation-secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FDS_AGENT_SERVICE_TOKEN");
    }

    @Test
    void configuredTokensMustBePairwiseDistinctWithoutExposingValue() {
        assertThatThrownBy(() -> validator(true, true, "shared-secret", "shared-secret", "agent-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use distinct values")
                .hasMessageNotContaining("shared-secret");
        assertThatThrownBy(() -> validator(false, true, "unused", "shared-secret", "shared-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use distinct values")
                .hasMessageNotContaining("shared-secret");
    }

    @Test
    void inactiveFeatureTokensAreNotCompared() {
        assertThatCode(() -> validator(true, false, "score-secret", "score-secret", "score-secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledFeaturesAcceptThreeDistinctTokens() {
        assertThatCode(() -> validator(true, true, "score-secret", "investigation-secret", "agent-secret"))
                .doesNotThrowAnyException();
    }

    private static FdsAgentIntegrationConfigurationValidator validator(
            boolean scoreEnabled,
            boolean investigationEnabled,
            String scoreToken,
            String investigationToken,
            String agentToken) {
        return new FdsAgentIntegrationConfigurationValidator(
                scoreEnabled, investigationEnabled, scoreToken, investigationToken, agentToken);
    }
}
