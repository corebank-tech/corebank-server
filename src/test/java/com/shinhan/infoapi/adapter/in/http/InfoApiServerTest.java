package com.shinhan.infoapi.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoapi.application.port.in.MartQueryUseCase;
import com.shinhan.infoapi.application.port.out.MartReadException;
import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.DepositBalanceTrend;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import com.shinhan.infoapi.domain.ProductSubscriptionSummary;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 실제 HttpServer를 임의 포트에 띄우고 인증·직렬화·오류 응답을 본다.
class InfoApiServerTest {

    private static final String SECRET = "test-shared-secret";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final FakeQuery query = new FakeQuery();
    private InfoApiServer server;

    @BeforeEach
    void start() throws IOException {
        server = new InfoApiServer(query, SECRET, 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("비밀 헤더가 없으면 401이고 조회를 호출하지 않는다")
    void missingSecretIsRejectedBeforeQuery() throws Exception {
        HttpResponse<String> response = send("GET", "/info/deposit-balances", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json.readTree(response.body()).get("data").isNull()).isTrue();
        assertThat(query.calls).isEmpty();
    }

    @Test
    @DisplayName("비밀이 틀리면 401이고 조회를 호출하지 않는다")
    void wrongSecretIsRejected() throws Exception {
        HttpResponse<String> response = send("GET", "/info/deposit-balances", "wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(query.calls).isEmpty();
    }

    @Test
    @DisplayName("인증 실패 응답에는 비밀이나 내부 정보가 없다")
    void unauthorizedBodyLeaksNothing() throws Exception {
        HttpResponse<String> response = send("GET", "/info/deposit-balances", "wrong-secret");

        assertThat(response.body()).doesNotContain(SECRET).doesNotContain("Exception");
    }

    @Test
    @DisplayName("GET이 아니면 405다")
    void nonGetIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/info/deposit-balances", SECRET);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(query.calls).isEmpty();
    }

    @Test
    @DisplayName("일별 잔액은 설계서 모양의 JSON으로 돌려준다")
    void depositBalancesJsonShape() throws Exception {
        query.trend = new DepositBalanceTrend(
                LocalDate.of(2026, 10, 26),
                List.of(
                        new DepositBalancePoint(LocalDate.of(2026, 10, 20), 1_523_400_000_000L),
                        new DepositBalancePoint(LocalDate.of(2026, 10, 21), 1_524_100_000_000L)));

        HttpResponse<String> response = send("GET", "/info/deposit-balances?from=2026-10-01&to=2026-10-26", SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("0000");
        assertThat(body.get("data").get("asOf").asString()).isEqualTo("2026-10-26");
        assertThat(body.get("data").get("items").get(0).get("baseDate").asString())
                .isEqualTo("2026-10-20");
        assertThat(body.get("data").get("items").get(0).get("totalBalance").asLong())
                .isEqualTo(1_523_400_000_000L);
        assertThat(query.calls).containsExactly("depositBalances 2026-10-01 2026-10-26");
    }

    @Test
    @DisplayName("from과 to를 생략하면 null로 조회한다")
    void omittedPeriodIsPassedAsNull() throws Exception {
        send("GET", "/info/deposit-balances", SECRET);

        assertThat(query.calls).containsExactly("depositBalances null null");
    }

    @Test
    @DisplayName("적재 이력이 없으면 asOf null과 빈 배열이다")
    void noLoadHistoryJson() throws Exception {
        HttpResponse<String> response = send("GET", "/info/deposit-balances", SECRET);

        JsonNode data = json.readTree(response.body()).get("data");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(data.get("asOf").isNull()).isTrue();
        assertThat(data.get("items").isArray()).isTrue();
        assertThat(data.get("items")).isEmpty();
    }

    @Test
    @DisplayName("가입·해지는 month를 yyyy-MM 문자열로 직렬화한다")
    void productSubscriptionsJsonShape() throws Exception {
        query.summary = new ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 26),
                YearMonth.of(2026, 10),
                120L,
                5L,
                List.of(new ProductSubscriptionLine(3, "DEP-12M", "쏠편한 \"정기\"예금", "DEPOSIT", 80, 3)));

        HttpResponse<String> response = send("GET", "/info/product-subscriptions?month=2026-10", SECRET);

        JsonNode data = json.readTree(response.body()).get("data");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(data.get("month").asString()).isEqualTo("2026-10");
        assertThat(data.get("subscribedCount").asLong()).isEqualTo(120L);
        assertThat(data.get("items").get(0).get("productName").asString()).isEqualTo("쏠편한 \"정기\"예금");
        assertThat(query.calls).containsExactly("productSubscriptions 2026-10");
    }

    @Test
    @DisplayName("응답에 고객 식별자나 가명 키 필드가 없다")
    void responseHasNoPiiFields() throws Exception {
        query.summary = new ProductSubscriptionSummary(
                LocalDate.of(2026, 10, 26),
                YearMonth.of(2026, 10),
                1L,
                0L,
                List.of(new ProductSubscriptionLine(3, "DEP-12M", "상품", "DEPOSIT", 1, 0)));

        HttpResponse<String> response = send("GET", "/info/product-subscriptions", SECRET);

        assertThat(response.body()).doesNotContain("customer").doesNotContain("account");
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400이고 조회를 호출하지 않는다")
    void invalidDateIsBadRequest() throws Exception {
        HttpResponse<String> response = send("GET", "/info/deposit-balances?from=2026-13-40", SECRET);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(query.calls).isEmpty();
    }

    @Test
    @DisplayName("month 형식이 틀리면 400이다")
    void invalidMonthIsBadRequest() throws Exception {
        HttpResponse<String> response = send("GET", "/info/product-subscriptions?month=2026-1", SECRET);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("마트 조회가 실패하면 500이고 내부 정보를 응답에 담지 않는다")
    void martFailureHidesInternals() throws Exception {
        query.failure =
                new MartReadException("마트 일별 잔액 조회에 실패했습니다.", new IllegalStateException("jdbc:mysql://secret-host/x"));

        HttpResponse<String> response = send("GET", "/info/deposit-balances", SECRET);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .doesNotContain("secret-host")
                .doesNotContain("jdbc")
                .doesNotContain("마트");
    }

    @Test
    @DisplayName("예기치 못한 오류도 500이고 예외 내용을 응답에 담지 않는다")
    void unexpectedFailureHidesInternals() throws Exception {
        query.failure = new IllegalStateException("내부 경로 /var/lib/info-mart");

        HttpResponse<String> response = send("GET", "/info/deposit-balances", SECRET);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain("/var/lib");
    }

    private HttpResponse<String> send(String method, String path, String secret) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (secret != null) {
            builder.header(InfoApiServer.SECRET_HEADER, secret);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class FakeQuery implements MartQueryUseCase {

        private final List<String> calls = new ArrayList<>();
        private DepositBalanceTrend trend = DepositBalanceTrend.noLoadHistory();
        private ProductSubscriptionSummary summary = ProductSubscriptionSummary.noLoadHistory(null);
        private RuntimeException failure;

        @Override
        public DepositBalanceTrend depositBalances(LocalDate from, LocalDate to) {
            calls.add("depositBalances " + from + " " + to);
            if (failure != null) {
                throw failure;
            }
            return trend;
        }

        @Override
        public ProductSubscriptionSummary productSubscriptions(YearMonth month) {
            calls.add("productSubscriptions " + month);
            return summary;
        }
    }
}
