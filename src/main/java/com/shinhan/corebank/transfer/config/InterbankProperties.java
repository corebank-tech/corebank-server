package com.shinhan.corebank.transfer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 타행 전문 송신 설정. 접속 주소만 바꿔 모의 대외기관 위치를 옮긴다(mock_external_server.md §5).
@ConfigurationProperties(prefix = "app.interbank")
public record InterbankProperties(
        String host,
        int port,
        Duration connectTimeout,
        Duration responseTimeout,
        CircuitBreaker circuitBreaker,
        Inquiry inquiry) {

    public record CircuitBreaker(
            float failureRateThreshold,
            int slidingWindowSize,
            int minimumNumberOfCalls,
            Duration waitDurationInOpenState) {}

    // fixed-delay는 @Scheduled가 프로퍼티로 직접 읽는다.
    public record Inquiry(Duration minAge, int batchSize) {}
}
