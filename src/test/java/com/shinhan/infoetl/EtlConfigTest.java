package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtlConfigTest {

    private static Map<String, String> fullEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("REPLICA_JDBC_URL", "jdbc:mysql://replica:3306/corebank");
        env.put("REPLICA_USER", "etl_reader");
        env.put("REPLICA_PASSWORD", "replica-secret-pw");
        env.put("MART_JDBC_URL", "jdbc:mysql://mart:3307/mart");
        env.put("MART_USER", "etl_writer");
        env.put("MART_PASSWORD", "mart-secret-pw");
        env.put("HMAC_KEY", "hmac-secret-key");
        return env;
    }

    @Test
    @DisplayName("환경변수가 모두 있으면 설정을 읽는다")
    void readsAllValues() {
        EtlConfig config = EtlConfig.from(fullEnv());

        assertThat(config.replicaUrl()).isEqualTo("jdbc:mysql://replica:3306/corebank");
        assertThat(config.replicaUser()).isEqualTo("etl_reader");
        assertThat(config.martUrl()).isEqualTo("jdbc:mysql://mart:3307/mart");
        assertThat(config.martUser()).isEqualTo("etl_writer");
        assertThat(config.hmacKey()).isEqualTo("hmac-secret-key".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("빠진 환경변수는 이름을 전부 모아 알려준다")
    void reportsEveryMissingName() {
        Map<String, String> env = fullEnv();
        env.remove("MART_PASSWORD");
        env.put("HMAC_KEY", "  ");
        env.remove("REPLICA_USER");

        assertThatThrownBy(() -> EtlConfig.from(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REPLICA_USER")
                .hasMessageContaining("MART_PASSWORD")
                .hasMessageContaining("HMAC_KEY");
    }

    @Test
    @DisplayName("오류 메시지에는 비밀번호나 키 값이 들어가지 않는다")
    void errorMessageHasNoSecretValues() {
        Map<String, String> env = fullEnv();
        env.remove("MART_USER");

        assertThatThrownBy(() -> EtlConfig.from(env))
                .hasMessageNotContaining("replica-secret-pw")
                .hasMessageNotContaining("mart-secret-pw")
                .hasMessageNotContaining("hmac-secret-key");
    }

    @Test
    @DisplayName("toString에는 비밀번호와 키가 찍히지 않는다")
    void toStringHidesSecrets() {
        String text = EtlConfig.from(fullEnv()).toString();

        assertThat(text)
                .doesNotContain("replica-secret-pw")
                .doesNotContain("mart-secret-pw")
                .doesNotContain("hmac-secret-key")
                .contains("jdbc:mysql://replica:3306/corebank");
    }
}
