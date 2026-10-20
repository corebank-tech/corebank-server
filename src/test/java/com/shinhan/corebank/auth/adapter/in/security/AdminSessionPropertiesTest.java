package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AdminSessionPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    // application.yml 의 키 이름이 레코드 컴포넌트와 맞는지는 여기서만 드러난다.
    @Test
    @DisplayName("session-timeout 설정을 Duration 으로 바인딩한다")
    void bindsSessionTimeout() {
        contextRunner
                .withPropertyValues("app.security.admin.session-timeout=30m")
                .run(context -> assertThat(
                                context.getBean(AdminSessionProperties.class).sessionTimeout())
                        .isEqualTo(Duration.ofMinutes(30)));
    }

    // 설정이 빠진 채 배포되면 관리자 첫 로그인이 아니라 기동에서 막혀야 한다.
    @Test
    @DisplayName("설정이 없으면 기동에 실패한다")
    void failsStartupWhenNotConfigured() {
        contextRunner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                // 어떤 바인딩 실패가 아니라 이 필드의 검증 실패여야 한다 — 원인 사슬까지 본다.
                .hasStackTraceContaining("sessionTimeout"));
    }

    // 0 이하는 만료 없는 세션이 되고, Integer.MAX_VALUE 초를 넘으면 초 단위 int 로 깎이면서
    // 설정한 적 없는 값이 된다. 둘 다 로그인 때가 아니라 기동에서 막혀야 한다.
    @ParameterizedTest(name = "session-timeout={0}")
    @ValueSource(strings = {"0m", "-1m", "24856d"})
    @DisplayName("session-timeout 이 1초~Integer.MAX_VALUE 초 밖이면 기동에 실패한다")
    void failsStartupWhenOutOfRange(String sessionTimeout) {
        contextRunner
                .withPropertyValues("app.security.admin.session-timeout=" + sessionTimeout)
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("sessionTimeout"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminSessionProperties.class)
    static class PropertiesConfig {}
}
