package com.shinhan.corebank.fds.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FastApiInvestigationClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsContractRequestAndMapsAcknowledgement() throws Exception {
        start(200, success());
        var result = client().investigate(input());
        assertThat(result.investigationId()).isEqualTo("inv-123");
        assertThat(result.transferHoldId()).isEqualTo(123L);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void classifiesPermanentAndTransientHttpFailures() throws Exception {
        start(400, "{}");
        assertFailure(false);
        stop();
        start(401, "{}");
        assertFailure(false);
        stop();
        start(502, "{}");
        assertFailure(true);
        stop();
        start(503, "{}");
        assertFailure(true);
        stop();
        start(504, "{}");
        assertFailure(true);
    }

    @Test
    void rejectsMalformedSuccessAndUnsafeConfiguration() throws Exception {
        start(200, "{}");
        assertFailure(false);
        assertThatThrownBy(() -> new FastApiInvestigationClient(URI.create("file:///tmp"), "token", new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FastApiInvestigationClient(
                        URI.create("http://localhost/path"), "token", new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FastApiInvestigationClient(URI.create("http://localhost"), "", new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionFailureIsRetryable() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var client = new FastApiInvestigationClient(
                URI.create("http://127.0.0.1:" + port), "test-secret", new ObjectMapper());
        assertThatThrownBy(() -> client.investigate(input()))
                .isInstanceOfSatisfying(
                        FdsInvestigationAgentPort.Failure.class,
                        failure -> assertThat(failure.retryable()).isTrue());
    }

    private void assertFailure(boolean retryable) {
        assertThatThrownBy(() -> client().investigate(input()))
                .isInstanceOfSatisfying(
                        FdsInvestigationAgentPort.Failure.class,
                        failure -> assertThat(failure.retryable()).isEqualTo(retryable));
    }

    private void start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/fds/investigations/run", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-secret");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("\"policyEvidence\"")
                    .contains("\"attemptNo\":1");
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private FastApiInvestigationClient client() {
        return new FastApiInvestigationClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-secret", new ObjectMapper());
    }

    private static FdsInvestigationInput input() {
        return new FdsInvestigationInput(
                "inv-123",
                123L,
                1,
                "A",
                new FdsInvestigationInput.PolicyEvidence(3, 1_500_000L),
                new FdsInvestigationInput.BehaviorSummary(1, 2, 71, "PENDING", null, null, null),
                new FdsInvestigationInput.TransferSummary(500_000L, "***-**-1234"));
    }

    private static String success() {
        return "{\"code\":\"0000\",\"message\":\"정상 처리되었습니다.\",\"data\":{"
                + "\"investigationId\":\"inv-123\",\"transferHoldId\":123,\"status\":\"COMPLETED\"}}";
    }
}
