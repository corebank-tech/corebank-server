package com.shinhan.corebank.fds.adapter.out.http;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** 실제 Python 조사 실행 API를 호출하며 보고서는 별도 BE 콜백으로만 받는다. */
public final class FastApiInvestigationClient implements FdsInvestigationAgentPort {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String PATH = "/internal/fds/investigations/run";

    private final URI endpoint;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public FastApiInvestigationClient(URI baseUri, String token, ObjectMapper mapper) {
        Objects.requireNonNull(baseUri);
        if (!("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null
                || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null
                || !(baseUri.getPath().isEmpty() || "/".equals(baseUri.getPath()))) {
            throw new IllegalArgumentException("Python 조사 서비스 기본 주소가 올바르지 않습니다.");
        }
        if (token == null || token.isBlank() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Python 조사 서비스 토큰이 올바르지 않습니다.");
        }
        this.endpoint = baseUri.resolve(PATH);
        this.token = token;
        this.mapper = Objects.requireNonNull(mapper);
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Result investigate(FdsInvestigationInput input) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(input)))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new Failure(response.statusCode() != 400 && response.statusCode() != 401);
            }
            Envelope envelope;
            try {
                envelope = mapper.readValue(response.body(), Envelope.class);
            } catch (RuntimeException exception) {
                throw new Failure(false);
            }
            if (envelope == null || !"0000".equals(envelope.code()) || envelope.data() == null) {
                throw new Failure(false);
            }
            return new Result(
                    envelope.data().investigationId(),
                    envelope.data().transferHoldId(),
                    envelope.data().status());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new Failure(true);
        } catch (IOException exception) {
            throw new Failure(true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new Failure(true);
        }
    }

    record Envelope(String code, String message, Data data) {}

    record Data(String investigationId, long transferHoldId, String status) {}
}
