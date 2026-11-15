package com.shinhan.corebank.fds.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort.Failure;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 실제 HTTP 응답을 이용해 Python 점수 계약의 성공·오류 매핑을 확인한다. */
class FastApiBehaviorScoreClientTest {

    private static final UUID FLOW_ID = UUID.fromString("3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90");
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsPrivateRequestAndAcceptsOnlyMatchingScore() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(200, successBody(), calls);
        BehaviorScoreInferencePort.Result result = client().infer(validRequest());

        assertThat(calls).hasValue(1);
        assertThat(result.failure()).isNull();
        assertThat(result.score().value()).isEqualTo(0.81);
        assertThat(result.score().modelVersion()).isEqualTo("xgb-demo-v1");
    }

    @Test
    void rejectsInvalidInputBeforeCallingPython() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(200, successBody(), calls);
        var invalid = new BehaviorScoreInferencePort.Request(
                123, FLOW_ID, "p6-behavior-v1", new BehaviorScoreInferencePort.Features(1, 2, null, 101, 100));

        assertThat(client().infer(invalid).failure()).isEqualTo(Failure.INPUT_INVALID);
        assertThat(calls).hasValue(0);
    }

    @Test
    void mapsContractedFailureStatuses() throws Exception {
        int[] statuses = {400, 401, 409, 503, 504, 500};
        Failure[] failures = {
            Failure.INPUT_INVALID,
            Failure.SERVICE_UNAVAILABLE,
            Failure.MODEL_UNAVAILABLE,
            Failure.MODEL_UNAVAILABLE,
            Failure.TIMEOUT,
            Failure.INFERENCE_FAILED
        };
        for (int i = 0; i < statuses.length; i++) {
            stop();
            start(statuses[i], "{\"code\":\"SCORE_ERROR\",\"message\":\"error\",\"data\":null}", new AtomicInteger());
            assertThat(client().infer(validRequest()).failure()).isEqualTo(failures[i]);
        }
    }

    @Test
    void rejectsMismatchedIdentityAndOutOfRangeScore() throws Exception {
        start(200, successBody().replace("0.81", "1.2"), new AtomicInteger());
        assertThat(client().infer(validRequest()).failure()).isEqualTo(Failure.INFERENCE_FAILED);
        stop();
        start(200, successBody().replace("\"transferHoldId\":123", "\"transferHoldId\":124"), new AtomicInteger());
        assertThat(client().infer(validRequest()).failure()).isEqualTo(Failure.INFERENCE_FAILED);
    }

    @Test
    void rejectsMalformedSuccessBodies() throws Exception {
        String[] bodies = {
            "not-json",
            "{}",
            successBody().replace("\"0000\"", "\"SCORE_ERROR\""),
            successBody().replace("\"flowId\":\"" + FLOW_ID, "\"flowId\":\"" + UUID.randomUUID()),
            successBody().replace("\"p6-behavior-v1\"", "\"another-version\""),
            successBody().replace("\"xgb-demo-v1\"", "\"\""),
            successBody().replace("0.81", "null")
        };
        for (String body : bodies) {
            stop();
            start(200, body, new AtomicInteger());
            assertThat(client().infer(validRequest()).failure()).isEqualTo(Failure.INFERENCE_FAILED);
        }
    }

    @Test
    void invalidFeatureBoundariesNeverReachPython() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(200, successBody(), calls);
        var valid = validRequest();
        var invalid = new BehaviorScoreInferencePort.Request[] {
            null,
            new BehaviorScoreInferencePort.Request(0, FLOW_ID, "p6-behavior-v1", valid.features()),
            new BehaviorScoreInferencePort.Request(123, null, "p6-behavior-v1", valid.features()),
            new BehaviorScoreInferencePort.Request(123, FLOW_ID, "other", valid.features()),
            new BehaviorScoreInferencePort.Request(123, FLOW_ID, "p6-behavior-v1", null),
            withFeatures(-1, 2, null, null, 100),
            withFeatures(101, 2, null, null, 100),
            withFeatures(1, -1, null, null, 100),
            withFeatures(1, 101, null, null, 100),
            withFeatures(1, 2, -1, null, 100),
            withFeatures(1, 2, 1801, null, 100),
            withFeatures(1, 2, null, -1, 100),
            withFeatures(1, 2, null, 101, 100),
            withFeatures(1, 2, null, null, -1)
        };
        for (var input : invalid) {
            assertThat(client().infer(input).failure()).isEqualTo(Failure.INPUT_INVALID);
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsUnsafeConfigurationBeforeMakingRequests() {
        ObjectMapper mapper = new ObjectMapper();
        assertThatThrownBy(() -> new FastApiBehaviorScoreClient(URI.create("file:///tmp/model"), "secret", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FastApiBehaviorScoreClient(URI.create("http://localhost/path"), "secret", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FastApiBehaviorScoreClient(URI.create("http://localhost"), "", mapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FastApiBehaviorScoreClient(URI.create("http://localhost"), "a\nb", mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classifiesConnectionFailureWithoutRetry() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        var client = new FastApiBehaviorScoreClient(
                URI.create("http://127.0.0.1:" + unusedPort), "test-secret", new ObjectMapper());
        assertThat(client.infer(validRequest()).failure()).isEqualTo(Failure.SERVICE_UNAVAILABLE);
    }

    @Test
    void stopsWaitingAfterFiveSeconds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/fds/behavior-score/predict", exchange -> {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        assertThat(client().infer(validRequest()).failure()).isEqualTo(Failure.TIMEOUT);
    }

    @Test
    void preservesInterruptionWhenCallIsStopped() throws Exception {
        start(200, successBody(), new AtomicInteger());
        Thread.currentThread().interrupt();
        try {
            assertThat(client().infer(validRequest()).failure()).isEqualTo(Failure.SERVICE_UNAVAILABLE);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static BehaviorScoreInferencePort.Request withFeatures(
            int back, int revision, Integer active, Integer recipient, int total) {
        return new BehaviorScoreInferencePort.Request(
                123,
                FLOW_ID,
                "p6-behavior-v1",
                new BehaviorScoreInferencePort.Features(back, revision, active, recipient, total));
    }

    private void start(int status, String body, AtomicInteger calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/fds/behavior-score/predict", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-secret");
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    private FastApiBehaviorScoreClient client() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new FastApiBehaviorScoreClient(base, "test-secret", new ObjectMapper());
    }

    private static BehaviorScoreInferencePort.Request validRequest() {
        return new BehaviorScoreInferencePort.Request(
                123, FLOW_ID, "p6-behavior-v1", new BehaviorScoreInferencePort.Features(1, 2, 71, 15, 100));
    }

    private static String successBody() {
        return "{\"code\":\"0000\",\"message\":\"정상 처리되었습니다.\",\"data\":{\"transferHoldId\":123,\"flowId\":\""
                + FLOW_ID
                + "\",\"behaviorRiskScore\":0.81,\"modelVersion\":\"xgb-demo-v1\",\"featureVersion\":\"p6-behavior-v1\"}}";
    }
}
