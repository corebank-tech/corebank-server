package com.shinhan.infoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InfoApiConfigTest {

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("MART_JDBC_URL", "jdbc:mysql://127.0.0.1:3307/info_mart");
        env.put("MART_USER", "info_api_reader");
        env.put("MART_PASSWORD", "mart-password-value");
        env.put("INFO_API_SECRET", "shared-secret-value");
        return env;
    }

    @Test
    @DisplayName("필수 값이 모두 있으면 읽고 포트는 기본 8081이다")
    void readsRequiredValuesWithDefaultPort() {
        InfoApiConfig config = InfoApiConfig.from(validEnv());

        assertThat(config.martUser()).isEqualTo("info_api_reader");
        assertThat(config.sharedSecret()).isEqualTo("shared-secret-value");
        assertThat(config.port()).isEqualTo(8081);
    }

    @Test
    @DisplayName("INFO_API_PORT를 주면 그 포트를 쓴다")
    void readsCustomPort() {
        Map<String, String> env = validEnv();
        env.put("INFO_API_PORT", " 9100 ");

        assertThat(InfoApiConfig.from(env).port()).isEqualTo(9100);
    }

    @Test
    @DisplayName("빠진 필수 환경변수는 이름만 오류에 담고 값은 담지 않는다")
    void reportsMissingNamesOnly() {
        Map<String, String> env = validEnv();
        env.remove("INFO_API_SECRET");
        env.put("MART_USER", " ");

        assertThatThrownBy(() -> InfoApiConfig.from(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INFO_API_SECRET")
                .hasMessageContaining("MART_USER")
                .hasMessageNotContaining("mart-password-value");
    }

    @Test
    @DisplayName("포트가 숫자가 아니거나 범위를 벗어나면 거절한다")
    void rejectsInvalidPort() {
        for (String port : new String[] {"abc", "0", "65536", "-1"}) {
            Map<String, String> env = validEnv();
            env.put("INFO_API_PORT", port);

            assertThatThrownBy(() -> InfoApiConfig.from(env))
                    .as("INFO_API_PORT=%s", port)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INFO_API_PORT");
        }
    }

    @Test
    @DisplayName("toString은 비밀번호와 공유 비밀을 찍지 않는다")
    void toStringHidesSecrets() {
        String text = InfoApiConfig.from(validEnv()).toString();

        assertThat(text).contains("jdbc:mysql://127.0.0.1:3307/info_mart");
        assertThat(text).doesNotContain("mart-password-value").doesNotContain("shared-secret-value");
    }
}
