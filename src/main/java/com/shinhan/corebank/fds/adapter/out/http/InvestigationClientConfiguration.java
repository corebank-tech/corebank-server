package com.shinhan.corebank.fds.adapter.out.http;

import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** 조사 워커를 켠 환경에서만 Python 실행 클라이언트를 구성한다. */
@Configuration(proxyBeanMethods = false)
public class InvestigationClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.fds.investigation", name = "enabled", havingValue = "true")
    FdsInvestigationAgentPort investigationAgentPort(
            @Value("${app.fds.investigation.base-url}") URI baseUri,
            @Value("${FDS_INVESTIGATION_SERVICE_TOKEN}") String token,
            ObjectMapper mapper) {
        return new FastApiInvestigationClient(baseUri, token, mapper);
    }
}
