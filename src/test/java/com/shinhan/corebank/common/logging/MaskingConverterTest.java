package com.shinhan.corebank.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MaskingConverterTest {

    private String render(String message) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("mask", MaskingConverter::new);
        layout.setPattern("%mask(%msg)");
        layout.start();

        Logger logger = context.getLogger(MaskingConverterTest.class);
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, message, null, null);
        return layout.doLayout(event);
    }

    @Test
    void 계좌번호_12자리는_가려진다() {
        assertThat(render("계좌 123456789012로 송금했습니다")).isEqualTo("계좌 123******012로 송금했습니다");
    }

    @Test
    void 휴대폰번호_01로_시작하는_11자리는_가려진다() {
        assertThat(render("연락처 01012345678")).isEqualTo("연락처 010****5678");
    }

    @Test
    void 같은_메시지에_계좌번호와_휴대폰번호가_같이_있어도_둘다_가려진다() {
        assertThat(render("계좌 123456789012 연락처 01012345678")).isEqualTo("계좌 123******012 연락처 010****5678");
    }

    @Test
    void 숫자가_13자리면_12자리_패턴으로_오인해_자르지_않는다() {
        assertThat(render("번호 1234567890123")).isEqualTo("번호 1234567890123");
    }

    @Test
    void 마스킹_대상이_없으면_원문_그대로다() {
        assertThat(render("정상 처리되었습니다")).isEqualTo("정상 처리되었습니다");
    }
}
