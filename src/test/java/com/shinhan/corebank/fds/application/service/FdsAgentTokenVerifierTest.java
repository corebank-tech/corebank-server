package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class FdsAgentTokenVerifierTest {
    @Test
    void acceptsOnlyExactBearerToken() {
        FdsAgentTokenVerifier verifier = new FdsAgentTokenVerifier("agent-secret");

        assertThatCode(() -> verifier.verify("Bearer agent-secret")).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify("Bearer score-secret")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void blankConfiguredTokenNeverOpensInternalApi() {
        FdsAgentTokenVerifier verifier = new FdsAgentTokenVerifier(" ");

        assertThatThrownBy(() -> verifier.verify("Bearer  ")).isInstanceOf(BusinessException.class);
    }
}
