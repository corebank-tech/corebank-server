package com.shinhan.corebank.fds.adapter.out.http;

import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** #932 내부 HTTP 계약. 이 클라이언트는 점수나 보류 상태를 저장하지 않는다. */
public final class FastApiBehaviorScoreClient implements BehaviorScoreInferencePort {

    private static final String PATH = "/internal/fds/behavior-score/predict";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final URI endpoint;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public FastApiBehaviorScoreClient(URI baseUri, String token, ObjectMapper mapper) {
        Objects.requireNonNull(baseUri);
        if ((!"http".equalsIgnoreCase(baseUri.getScheme()) && !"https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null
                || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null
                || !baseUri.getPath().isEmpty() && !"/".equals(baseUri.getPath())) {
            throw new IllegalArgumentException("Python 서비스 기본 주소가 올바르지 않습니다.");
        }
        this.endpoint = baseUri.resolve(PATH);
        this.token = Objects.requireNonNull(token);
        if (token.isBlank() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Python 서비스 토큰이 올바르지 않습니다.");
        }
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Result infer(Request input) {
        if (!valid(input)) {
            return Result.unavailable(Failure.INPUT_INVALID);
        }
        try {
            byte[] body = mapper.writeValueAsBytes(input);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return map(response, input);
        } catch (java.net.http.HttpTimeoutException e) {
            return Result.unavailable(Failure.TIMEOUT);
        } catch (IOException e) {
            return Result.unavailable(Failure.SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.unavailable(Failure.SERVICE_UNAVAILABLE);
        } catch (JacksonException e) {
            return Result.unavailable(Failure.INFERENCE_FAILED);
        }
    }

    private Result map(HttpResponse<byte[]> response, Request request) {
        // Python 오류는 내부 사유로만 바꾸고 원문 응답이나 토큰을 관리자에게 전달하지 않는다.
        if (response.statusCode() != 200) {
            return Result.unavailable(
                    switch (response.statusCode()) {
                        case 400 -> Failure.INPUT_INVALID;
                        case 401 -> Failure.SERVICE_UNAVAILABLE;
                        case 409, 503 -> Failure.MODEL_UNAVAILABLE;
                        case 504 -> Failure.TIMEOUT;
                        default -> Failure.INFERENCE_FAILED;
                    });
        }
        try {
            Envelope envelope = mapper.readValue(response.body(), Envelope.class);
            Data data = envelope.data();
            if (!"0000".equals(envelope.code())
                    || data == null
                    || data.transferHoldId() != request.transferHoldId()
                    || !request.flowId().toString().equals(data.flowId())
                    || !request.featureVersion().equals(data.featureVersion())
                    || data.behaviorRiskScore() == null
                    || !Double.isFinite(data.behaviorRiskScore())
                    || data.behaviorRiskScore() < 0
                    || data.behaviorRiskScore() > 1
                    || data.modelVersion() == null
                    || data.modelVersion().isBlank()) {
                return Result.unavailable(Failure.INFERENCE_FAILED);
            }
            return Result.available(new Score(data.behaviorRiskScore(), data.modelVersion(), data.featureVersion()));
        } catch (RuntimeException e) {
            return Result.unavailable(Failure.INFERENCE_FAILED);
        }
    }

    private static boolean valid(Request request) {
        if (request == null
                || request.features() == null
                || request.flowId() == null
                || request.featureVersion() == null) {
            return false;
        }
        Features f = request.features();
        return request.transferHoldId() > 0
                && "p6-behavior-v1".equals(request.featureVersion())
                && f.backCount() >= 0
                && f.backCount() <= 100
                && f.amountRevisionCount() >= 0
                && f.amountRevisionCount() <= 100
                && (f.confirmActiveSeconds() == null
                        || f.confirmActiveSeconds() >= 0 && f.confirmActiveSeconds() <= 1800)
                && f.totalFlowSeconds() >= 0
                && (f.recipientConfirmSeconds() == null
                        || f.recipientConfirmSeconds() >= 0 && f.recipientConfirmSeconds() <= f.totalFlowSeconds());
    }

    record Envelope(String code, String message, Data data) {}

    record Data(
            long transferHoldId, String flowId, Double behaviorRiskScore, String modelVersion, String featureVersion) {}
}
