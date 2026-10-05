package com.shinhan.corebank.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

// src/main/resources/logback-spring.xml 을 실제로 로드해 JSON 출력·마스킹·correlationId를 검증한다
class LogbackConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 이 테스트가 로거 설정을 통째로 reset/재로드하므로, 뒤에 돌 다른 테스트의 로깅에 영향 없게 원복한다
    @AfterEach
    void restoreLoggingSystem() throws Exception {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getClassLoader().getResource("logback-spring.xml"));
    }

    @Test
    void 실제_logback_spring_xml을_로드해서_콘솔에_JSON으로_찍고_마스킹과_correlationId를_확인한다() throws Exception {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getClassLoader().getResource("logback-spring.xml"));

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(captured));

        String line;
        try {
            MDC.put("correlationId", "test-correlation-id");
            MDC.put("customerId", "42");
            Logger logger = context.getLogger(LogbackConfigTest.class);
            logger.info("계좌 123456789012로 이체 완료, 연락처 01012345678");
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
        line = captured.toString().trim();

        var json = objectMapper.readTree(line);
        assertThat(json.get("correlationId").asText()).isEqualTo("test-correlation-id");
        assertThat(json.get("customerId").asText()).isEqualTo("42");
        assertThat(json.get("message").asText()).isEqualTo("계좌 123******012로 이체 완료, 연락처 010****5678");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
    }
}
