package com.shinhan.corebank.info.adapter.out.api;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.info.application.port.out.InfoMartPort;
import com.shinhan.corebank.info.config.InfoApiProperties;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import com.shinhan.corebank.info.domain.exception.InfoErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// 온프레미스 정보계 API를 HTTP로 부른다(PH-77 ③). 연결·시간 초과·401·5xx·형식 오류는 모두 INF9001 로 바꾸고 원인은 로그에만 남긴다.
public class InfoApiClient implements InfoMartPort {

    private static final Logger log = LoggerFactory.getLogger(InfoApiClient.class);
    private static final String SECRET_HEADER = "X-Info-Api-Secret";
    private static final String SUCCESS_CODE = "0000";
    private static final String DEPOSIT_BALANCES = "/info/deposit-balances";
    private static final String PRODUCT_SUBSCRIPTIONS = "/info/product-subscriptions";

    private final String baseUrl;
    private final String secret;
    private final Duration readTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public InfoApiClient(InfoApiProperties properties, ObjectMapper objectMapper) {
        String url = properties.baseUrl().strip();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        URI uri = URI.create(this.baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null) {
            throw new IllegalArgumentException("정보계 API 주소는 http 또는 https 이고 호스트가 있어야 합니다.");
        }
        this.secret = properties.secret();
        this.readTimeout = properties.readTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
        TrendEnvelope body = get(DEPOSIT_BALANCES, query(param("from", from), param("to", to)), TrendEnvelope.class);
        DepositBalanceTrend trend = data(body.code(), body.data(), DEPOSIT_BALANCES);
        if (trend.items() == null || trend.items().contains(null)) {
            throw invalidData(DEPOSIT_BALANCES);
        }
        return trend;
    }

    @Override
    public ProductSubscriptionSummary productSubscriptions(YearMonth month) {
        SummaryEnvelope body = get(PRODUCT_SUBSCRIPTIONS, query(param("month", month)), SummaryEnvelope.class);
        ProductSubscriptionSummary summary = data(body.code(), body.data(), PRODUCT_SUBSCRIPTIONS);
        if (summary.items() == null || summary.items().contains(null)) {
            throw invalidData(PRODUCT_SUBSCRIPTIONS);
        }
        return summary;
    }

    private <T> T get(String path, String query, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + query))
                .timeout(readTimeout)
                .header(SECRET_HEADER, secret)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("정보계 API 응답 오류 - path={}, status={}", path, response.statusCode());
                throw new BusinessException(InfoErrorCode.CONNECTION_FAILED);
            }
            return objectMapper.readValue(response.body(), type);
        } catch (IOException e) {
            log.error("정보계 API 호출 실패 - path={}", path, e);
            throw new BusinessException(InfoErrorCode.CONNECTION_FAILED, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("정보계 API 호출 중단 - path={}", path, e);
            throw new BusinessException(InfoErrorCode.CONNECTION_FAILED, e);
        } catch (JacksonException e) {
            log.error("정보계 API 응답 형식 오류 - path={}", path, e);
            throw new BusinessException(InfoErrorCode.CONNECTION_FAILED, e);
        }
    }

    private static <T> T data(String code, T data, String path) {
        if (!SUCCESS_CODE.equals(code) || data == null) {
            log.error("정보계 API 응답 코드 이상 - path={}, code={}", path, code);
            throw new BusinessException(InfoErrorCode.CONNECTION_FAILED);
        }
        return data;
    }

    private static BusinessException invalidData(String path) {
        log.error("정보계 API 응답에 필수 값이 없음 - path={}", path);
        return new BusinessException(InfoErrorCode.CONNECTION_FAILED);
    }

    private static String param(String name, Object value) {
        return value == null ? null : name + "=" + value;
    }

    private static String query(String... params) {
        String joined = Arrays.stream(params).filter(Objects::nonNull).collect(Collectors.joining("&"));
        return joined.isEmpty() ? "" : "?" + joined;
    }

    record TrendEnvelope(String code, String message, DepositBalanceTrend data) {}

    record SummaryEnvelope(String code, String message, ProductSubscriptionSummary data) {}
}
