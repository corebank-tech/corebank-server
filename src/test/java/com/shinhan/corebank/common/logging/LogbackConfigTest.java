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

    // 클라이언트가 X-Correlation-Id에 JSON 특수문자(쌍따옴표·개행)를 넣어 로그 구조를 깨거나
    // 가짜 필드를 주입하려는 시도를 할 수 있다 - 라이브러리가 이스케이프하는지 직접 확인한다
    @Test
    void correlationId에_쌍따옴표와_개행이_있어도_JSON이_깨지지_않는다() throws Exception {
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getClassLoader().getResource("logback-spring.xml"));

        String malicious = "abc\",\"injected\":\"evil\nvalue";

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(captured));

        String line;
        try {
            MDC.put("correlationId", malicious);
            Logger logger = context.getLogger(LogbackConfigTest.class);
            logger.info("정상 처리");
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
        line = captured.toString().trim();

        var json = objectMapper.readTree(line);
        assertThat(json.get("correlationId").asText()).isEqualTo(malicious);
        assertThat(json.has("injected")).isFalse();
    }

    // 클라이언트가 X-Correlation-Id 헤더에 계좌번호 형식 값을 그대로 넣으면, message가 아니라
    // correlationId 필드로 로그에 남아 마스킹을 비켜갈 수 있었다(PR #560 리뷰). 필드 자체도 가려지는지 확인한다
    @Test
    void correlationId에_계좌번호_형식_값이_있어도_가려진다() throws Exception {
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
            MDC.put("correlationId", "123456789012");
            Logger logger = context.getLogger(LogbackConfigTest.class);
            logger.info("정상 처리");
        } finally {
            System.setOut(originalOut);
            MDC.clear();
        }
        line = captured.toString().trim();

        var json = objectMapper.readTree(line);
        assertThat(json.get("correlationId").asText()).isEqualTo("123******012");
    }

    // log.error(msg, e) 로 찍은 처리되지 않은 예외의 스택이 JSON에서 통째로 사라졌었다(PR #560 리뷰).
    // stackTrace 필드에 실제로 남는지, 예외 메시지 속 계좌번호도 마스킹되는지 같이 확인한다
    @Test
    void 예외를_찍으면_stackTrace_필드에_마스킹된_스택이_남는다() throws Exception {
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
            Logger logger = context.getLogger(LogbackConfigTest.class);
            logger.error("처리되지 않은 예외 발생", new RuntimeException("계좌 123456789012 처리 실패"));
        } finally {
            System.setOut(originalOut);
        }
        line = captured.toString().trim();

        var json = objectMapper.readTree(line);
        String stackTrace = json.get("stackTrace").asText();
        assertThat(stackTrace).contains("RuntimeException");
        assertThat(stackTrace).contains("123******012");
        assertThat(stackTrace).doesNotContain("123456789012");
    }
}
