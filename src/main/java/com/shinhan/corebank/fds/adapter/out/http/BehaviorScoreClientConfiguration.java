package com.shinhan.corebank.fds.adapter.out.http;

import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** 실제 조사 워커가 연결될 때만 내부 Python 클라이언트를 활성화한다. */
@Configuration(proxyBeanMethods = false)
public class BehaviorScoreClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.fds.score", name = "enabled", havingValue = "true")
    BehaviorScoreInferencePort behaviorScoreInferencePort(
            @Value("${app.fds.score.base-url}") URI baseUri,
            @Value("${FDS_SCORE_SERVICE_TOKEN}") String token,
            ObjectMapper mapper) {
        return new FastApiBehaviorScoreClient(baseUri, token, mapper);
    }
}
