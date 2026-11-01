package com.shinhan.corebank.otp.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// 설정값에 따라 OTP 해시 구현이 하나만 주입되는지 검증한다. 기본값은 기존 BCrypt다.
class OtpCodeHashAlgorithmToggleTest {

    private static final String KEY = encodedTestKey();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    PasswordEncoderConfiguration.class, BCryptOtpCodeHashAdapter.class, HmacOtpCodeHashAdapter.class);

    @Test
    @DisplayName("설정이 없으면 기존 BCrypt 구현만 주입된다")
    void usesBcryptByDefault() {
        runner.withPropertyValues("APP_OTP_CODE_HASH_KEY_BASE64=" + KEY).run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(OtpCodeHashPort.class)).isInstanceOf(BCryptOtpCodeHashAdapter.class);
            assertThat(context.getBeansOfType(HmacOtpCodeHashAdapter.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("hmac으로 지정하면 HMAC 구현만 주입된다")
    void usesHmacWhenSelected() {
        runner.withPropertyValues("app.otp.code-hash.algorithm=hmac", "APP_OTP_CODE_HASH_KEY_BASE64=" + KEY)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(OtpCodeHashPort.class)).isInstanceOf(HmacOtpCodeHashAdapter.class);
                    assertThat(context.getBeansOfType(BCryptOtpCodeHashAdapter.class))
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("bcrypt로 명시해도 BCrypt 구현만 주입된다")
    void usesBcryptWhenSelectedExplicitly() {
        runner.withPropertyValues("app.otp.code-hash.algorithm=bcrypt", "APP_OTP_CODE_HASH_KEY_BASE64=" + KEY)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(OtpCodeHashPort.class)).isInstanceOf(BCryptOtpCodeHashAdapter.class);
                });
    }

    @Test
    @DisplayName("hmac인데 키가 없으면 임시 키로 대체하지 않고 기동을 실패시킨다")
    void failsWhenHmacKeyIsMissing() {
        runner.withPropertyValues("app.otp.code-hash.algorithm=hmac", "APP_OTP_CODE_HASH_KEY_BASE64=")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    // 키 문자열을 소스에 박지 않고 길이만 맞춰 만든다.
    private static String encodedTestKey() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 1);
        return Base64.getEncoder().encodeToString(key);
    }

    @Configuration(proxyBeanMethods = false)
    static class PasswordEncoderConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
