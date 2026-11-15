package com.shinhan.corebank.fds.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 로컬 Python 서비스를 켰을 때 실제 모델 추론 HTTP 계약을 확인한다. */
class FastApiPythonLiveIntegrationTest {
    @Test
    void callsRunningPythonModel() {
        String url = System.getenv("FDS_SCORE_TEST_URL");
        String token = System.getenv("FDS_SCORE_SERVICE_TOKEN");
        assumeTrue(url != null && token != null, "로컬 Python 서비스가 설정된 경우만 실행");

        var client = new FastApiBehaviorScoreClient(URI.create(url), token, new ObjectMapper());
        var result = client.infer(new BehaviorScoreInferencePort.Request(
                951001L,
                UUID.fromString("3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90"),
                "p6-behavior-v1",
                new BehaviorScoreInferencePort.Features(1, 1, 71, 15, 100)));

        assertThat(result.score()).isNotNull();
        assertThat(result.score().value()).isBetween(0.0, 1.0);
        assertThat(result.score().modelVersion()).isNotBlank();
        assertThat(result.score().featureVersion()).isEqualTo("p6-behavior-v1");
    }
}
