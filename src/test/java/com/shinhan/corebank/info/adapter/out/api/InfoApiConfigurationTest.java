package com.shinhan.corebank.info.adapter.out.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.config.InfoApiProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("정보계 API 설정")
class InfoApiConfigurationTest {

    private static final Duration CONNECT = Duration.ofSeconds(1);
    private static final Duration READ = Duration.ofSeconds(5);

    private final InfoApiConfiguration configuration = new InfoApiConfiguration();

    @Test
    @DisplayName("주소가 비어 있으면 적재 이력 없음 응답을 쓴다")
    void blankBaseUrlUsesNoLoadHistory() {
        InfoMartPort port =
                configuration.infoMartPort(new InfoApiProperties("", "", CONNECT, READ), new ObjectMapper());

        assertThat(port).isInstanceOf(NoLoadHistoryInfoMart.class);
    }

    @Test
    @DisplayName("주소가 없어도(null) 적재 이력 없음 응답을 쓴다")
    void nullBaseUrlUsesNoLoadHistory() {
        InfoMartPort port =
                configuration.infoMartPort(new InfoApiProperties(null, null, CONNECT, READ), new ObjectMapper());

        assertThat(port).isInstanceOf(NoLoadHistoryInfoMart.class);
    }

    @Test
    @DisplayName("주소와 비밀이 있으면 정보계 API 클라이언트를 쓴다")
    void configuredUsesClient() {
        InfoMartPort port = configuration.infoMartPort(
                new InfoApiProperties("http://10.0.0.1:9400", "secret", CONNECT, READ), new ObjectMapper());

        assertThat(port).isInstanceOf(InfoApiClient.class);
    }

    @Test
    @DisplayName("주소만 있고 비밀이 없으면 설정 단계에서 실패한다")
    void baseUrlWithoutSecretFails() {
        assertThatThrownBy(() -> new InfoApiProperties("http://10.0.0.1:9400", " ", CONNECT, READ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoApiProperties("http://10.0.0.1:9400", null, CONNECT, READ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제한 시간이 0 이하이면 설정 단계에서 실패한다")
    void nonPositiveTimeoutFails() {
        assertThatThrownBy(() -> new InfoApiProperties("", "", Duration.ZERO, READ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoApiProperties("", "", CONNECT, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoApiProperties("", "", null, READ))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
