package com.shinhan.corebank.info.adapter.out.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.info.config.InfoApiProperties;
import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import com.shinhan.corebank.info.domain.exception.InfoErrorCode;
import com.shinhan.infoapi.adapter.in.http.InfoApiServer;
import com.shinhan.infoapi.application.port.in.MartQueryUseCase;
import com.shinhan.infoapi.application.port.out.MartReadException;
import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// 실제 정보계 API 서버를 임의 포트에 띄워 WAS 어댑터가 응답을 읽고 오류를 INF9001 로 바꾸는지 본다.
@DisplayName("정보계 API 클라이언트")
class InfoApiClientTest {

    private static final String SECRET = "test-shared-secret";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    private final ObjectMapper json = new ObjectMapper();
    private final FakeQuery query = new FakeQuery();
    private InfoApiServer server;

    @BeforeEach
    void start() throws IOException {
        server = new InfoApiServer(query, SECRET, "127.0.0.1", 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("일별 수신 잔액 추이를 읽고 기간을 쿼리로 넘긴다")
    void readsDepositBalances() {
        query.trend = new com.shinhan.infoapi.domain.DepositBalanceTrend(
                LocalDate.of(2026, 10, 28),
                List.of(
                        new DepositBalancePoint(LocalDate.of(2026, 10, 27), 1_000L),
                        new DepositBalancePoint(LocalDate.of(2026, 10, 28), 2_500L)));

        DepositBalanceTrend result =
                client(SECRET).depositBalances(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28));

        assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(result.items())
                .extracting(DepositBalanceTrend.Point::baseDate, DepositBalanceTrend.Point::totalBalance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 27), 1_000L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 10, 28), 2_500L));
        assertThat(query.calls).containsExactly("deposit 2026-10-27 2026-10-28");
    }

    @Test
    @DisplayName("기간을 생략하면 쿼리 없이 호출하고 적재 이력 없음 응답을 읽는다")
    void readsNoLoadHistoryWithoutParams() {
        query.trend = com.shinhan.infoapi.domain.DepositBalanceTrend.noLoadHistory();

        DepositBalanceTrend result = client(SECRET).depositBalances(null, null);

        assertThat(result.asOf()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(query.calls).containsExactly("deposit null null");
    }

    @Test
    @DisplayName("상품 가입 요약을 읽고 월을 쿼리로 넘긴다")
    void readsProductSubscriptions() {
        query.summary = new com.shinhan.infoapi.domain.ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 28),
                YearMonth.of(2026, 10),
                7L,
                2L,
                List.of(new ProductSubscriptionLine(1L, "DEP-001", "자유입출금", "DEPOSIT", 7L, 2L)));

        ProductSubscriptionSummary result = client(SECRET).productSubscriptions(YearMonth.of(2026, 10));

        assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(result.month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(result.subscribedCount()).isEqualTo(7L);
        assertThat(result.closedCount()).isEqualTo(2L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productCode()).isEqualTo("DEP-001");
        assertThat(query.calls).containsExactly("product 2026-10");
    }

    @Test
    @DisplayName("비밀이 틀리면 INF9001 이고 응답에 비밀이 없다")
    void wrongSecretBecomesConnectionFailed() {
        assertThatThrownBy(() -> client("wrong-secret").depositBalances(null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(InfoErrorCode.CONNECTION_FAILED);
                    assertThat(e.getMessage()).doesNotContain("wrong-secret").doesNotContain(SECRET);
                });
        assertThat(query.calls).isEmpty();
    }

    @Test
    @DisplayName("정보계 API가 500을 주면 INF9001 이다")
    void serverErrorBecomesConnectionFailed() {
        query.failure = new MartReadException("마트 읽기 실패", new IllegalStateException("db down"));

        assertThatThrownBy(() -> client(SECRET).productSubscriptions(null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(InfoErrorCode.CONNECTION_FAILED);
                    assertThat(e.getMessage()).doesNotContain("db down").doesNotContain("마트 읽기 실패");
                });
    }

    @Test
    @DisplayName("응답이 시간 제한을 넘기면 INF9001 이다")
    void slowResponseBecomesConnectionFailed() {
        query.delay = Duration.ofSeconds(2);

        assertThatThrownBy(() -> client(SECRET).depositBalances(null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("서버에 연결하지 못하면 INF9001 이다")
    void connectionRefusedBecomesConnectionFailed() {
        InfoApiClient client = client(SECRET);
        server.stop();

        assertThatThrownBy(() -> client.depositBalances(null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("200 이어도 JSON 이 아니면 INF9001 이다")
    void malformedBodyBecomesConnectionFailed() throws Exception {
        assertRawResponseFails(200, "not json");
    }

    @Test
    @DisplayName("200 이어도 성공 코드가 아니면 INF9001 이다")
    void nonSuccessCodeBecomesConnectionFailed() throws Exception {
        assertRawResponseFails(200, "{\"code\":\"9999\",\"message\":\"x\",\"data\":null}");
    }

    @Test
    @DisplayName("일별 수신 잔액의 items 가 없으면 INF9001 이다")
    void missingTrendItemsBecomesConnectionFailed() {
        query.trend = new com.shinhan.infoapi.domain.DepositBalanceTrend(LocalDate.of(2026, 10, 28), null);

        assertThatThrownBy(() -> client(SECRET).depositBalances(null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("일별 수신 잔액 목록에 null 원소가 있으면 INF9001 이다")
    void nullTrendItemBecomesConnectionFailed() {
        query.trend = new com.shinhan.infoapi.domain.DepositBalanceTrend(
                LocalDate.of(2026, 10, 28), Arrays.asList((DepositBalancePoint) null));

        assertThatThrownBy(() -> client(SECRET).depositBalances(null, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("상품 가입 요약의 items 가 없으면 INF9001 이다")
    void missingSummaryItemsBecomesConnectionFailed() {
        query.summary = new com.shinhan.infoapi.domain.ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 28), YearMonth.of(2026, 10), 0L, 0L, null);

        assertThatThrownBy(() -> client(SECRET).productSubscriptions(null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("상품 가입 요약 목록에 null 원소가 있으면 INF9001 이다")
    void nullSummaryItemBecomesConnectionFailed() {
        query.summary = new com.shinhan.infoapi.domain.ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 28), YearMonth.of(2026, 10), 0L, 0L, Arrays.asList((ProductSubscriptionLine)
                        null));

        assertThatThrownBy(() -> client(SECRET).productSubscriptions(null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
    }

    @Test
    @DisplayName("http · https 가 아니거나 호스트가 없는 주소는 만들 때 거부한다")
    void invalidBaseUrlIsRejectedAtCreation() {
        for (String url : new String[] {"ftp://10.0.0.1:8081", "10.0.0.1:8081", "http:///info", "not a url"}) {
            assertThatThrownBy(() ->
                            new InfoApiClient(new InfoApiProperties(url, SECRET, CONNECT_TIMEOUT, READ_TIMEOUT), json))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void assertRawResponseFails(int status, String body) throws Exception {
        HttpServer raw = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        raw.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        raw.start();
        try {
            InfoApiClient client = new InfoApiClient(
                    new InfoApiProperties(
                            "http://127.0.0.1:" + raw.getAddress().getPort(), SECRET, CONNECT_TIMEOUT, READ_TIMEOUT),
                    json);

            assertThatThrownBy(() -> client.depositBalances(null, null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(InfoErrorCode.CONNECTION_FAILED));
        } finally {
            raw.stop(0);
        }
    }

    private InfoApiClient client(String secret) {
        return new InfoApiClient(
                new InfoApiProperties("http://127.0.0.1:" + server.port() + "/", secret, CONNECT_TIMEOUT, READ_TIMEOUT),
                json);
    }

    private static final class FakeQuery implements MartQueryUseCase {

        final List<String> calls = new ArrayList<>();
        com.shinhan.infoapi.domain.DepositBalanceTrend trend =
                com.shinhan.infoapi.domain.DepositBalanceTrend.noLoadHistory();
        com.shinhan.infoapi.domain.ProductSubscriptionSummary summary =
                com.shinhan.infoapi.domain.ProductSubscriptionSummary.noLoadHistory(null);
        RuntimeException failure;
        Duration delay = Duration.ZERO;

        @Override
        public com.shinhan.infoapi.domain.DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
            calls.add("deposit " + from + " " + to);
            pause();
            return trend;
        }

        @Override
        public com.shinhan.infoapi.domain.ProductSubscriptionSummary productSubscriptions(YearMonth month) {
            calls.add("product " + month);
            pause();
            if (failure != null) {
                throw failure;
            }
            return summary;
        }

        private void pause() {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
