package com.shinhan.corebank.fds.adapter.out.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** FDS Agent 기능을 켠 환경의 서비스 토큰을 실제 요청 전에 검증한다. */
@Component
public class FdsAgentIntegrationConfigurationValidator {

    public FdsAgentIntegrationConfigurationValidator(
            @Value("${app.fds.score.enabled:false}") boolean scoreEnabled,
            @Value("${app.fds.investigation.enabled:false}") boolean investigationEnabled,
            @Value("${FDS_SCORE_SERVICE_TOKEN:}") String scoreToken,
            @Value("${FDS_INVESTIGATION_SERVICE_TOKEN:}") String investigationToken,
            @Value("${FDS_AGENT_SERVICE_TOKEN:}") String agentToken) {
        requireConfigured(scoreEnabled, scoreToken, "FDS_SCORE_SERVICE_TOKEN");
        requireConfigured(investigationEnabled, investigationToken, "FDS_INVESTIGATION_SERVICE_TOKEN");
        requireConfigured(investigationEnabled, agentToken, "FDS_AGENT_SERVICE_TOKEN");
        requireDistinct(scoreToken, investigationToken, "FDS_SCORE_SERVICE_TOKEN", "FDS_INVESTIGATION_SERVICE_TOKEN");
        requireDistinct(scoreToken, agentToken, "FDS_SCORE_SERVICE_TOKEN", "FDS_AGENT_SERVICE_TOKEN");
        requireDistinct(investigationToken, agentToken, "FDS_INVESTIGATION_SERVICE_TOKEN", "FDS_AGENT_SERVICE_TOKEN");
    }

    private static void requireConfigured(boolean enabled, String token, String variableName) {
        if (enabled && token.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured when its FDS feature is enabled");
        }
    }

    private static void requireDistinct(String left, String right, String leftName, String rightName) {
        if (!left.isBlank() && !right.isBlank() && left.equals(right)) {
            throw new IllegalStateException(leftName + " and " + rightName + " must use distinct values");
        }
    }
}
