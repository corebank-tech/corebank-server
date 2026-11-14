package com.shinhan.corebank.fds.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InvestigationClientConfigurationTest {
    @Test
    void createsFastApiClientWithExplicitEndpointAndToken() {
        var port = new InvestigationClientConfiguration()
                .investigationAgentPort(
                        URI.create("http://127.0.0.1:8000"),
                        "investigation-secret",
                        JsonMapper.builder().build());

        assertThat(port).isInstanceOf(FastApiInvestigationClient.class);
    }
}
