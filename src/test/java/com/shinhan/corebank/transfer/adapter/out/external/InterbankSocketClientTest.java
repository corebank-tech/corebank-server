package com.shinhan.corebank.transfer.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.config.InterbankProperties;
import com.shinhan.mockexternal.MockExternalServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 실제 모의 대외기관을 임의 포트로 띄워 응답코드 → 결과 매핑과 무응답·서킷을 확인한다.
@DisplayName("InterbankSocketClient 타행 전문 송신")
class InterbankSocketClientTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);
    private static final Clock CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final HttpClient http = HttpClient.newHttpClient();
    private MockExternalServer server;
    private int serialSeq;

    @BeforeEach
    void startServer() throws IOException {
        server = MockExternalServer.start("127.0.0.1", 0, 0);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private InterbankSocketClient client(int port, int minimumNumberOfCalls) {
        return new InterbankSocketClient(
                new InterbankProperties(
                        "127.0.0.1",
                        port,
                        Duration.ofMillis(500),
                        RESPONSE_TIMEOUT,
                        new InterbankProperties.CircuitBreaker(
                                50, minimumNumberOfCalls, minimumNumberOfCalls, Duration.ofMinutes(1)),
                        new InterbankProperties.Inquiry(Duration.ZERO, 20)),
                CLOCK);
    }

    private InterbankSocketClient client() {
        return client(server.port(), 100);
    }

    private InterbankRequest request(String accountNumber) {
        return new InterbankRequest(
                "004", "20261023WB" + String.format("%010d", ++serialSeq), accountNumber, 50_000L, "홍길동");
    }

    private void setFault(String query) throws IOException, InterruptedException {
        http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.adminPort() + "/faults?" + query))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    @DisplayName("0000이면 APPROVED와 상대 은행이 돌려준 예금주를 받는다")
    void send_approved_returnsPayeeName() {
        InterbankReply reply = client().send(request("1234567890123"));

        assertThat(reply.outcome()).isEqualTo(InterbankOutcome.APPROVED);
        assertThat(reply.payeeName()).isEqualTo("김민수");
        assertThat(reply.responseCode()).isEqualTo("0000");
    }

    @ParameterizedTest(name = "계좌 {0}… → {1}")
    @CsvSource({
        "1001000000000, ACCOUNT_NOT_FOUND, 1001",
        "1002000000000, ACCOUNT_UNAVAILABLE, 1002",
        "9999000000000, NOT_PROCESSED, 9999"
    })
    @DisplayName("거절·시스템 오류 응답은 확정 결과로 옮긴다")
    void send_rejected_mapsToConfirmedOutcome(String accountNumber, InterbankOutcome expected, String responseCode) {
        InterbankReply reply = client().send(request(accountNumber));

        assertThat(reply.outcome()).isEqualTo(expected);
        assertThat(reply.responseCode()).isEqualTo(responseCode);
        assertThat(reply.payeeName()).isNull();
    }

    @Test
    @DisplayName("같은 일련번호를 다시 보내 2001(중복)을 받으면 확정하지 않고 UNKNOWN이다")
    void send_duplicateSerial_isUnknown() {
        InterbankSocketClient client = client();
        InterbankRequest request = request("1234567890123");
        client.send(request);

        InterbankReply reply = client.send(request);

        assertThat(reply.outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
        assertThat(reply.responseCode()).isEqualTo("2001");
    }

    @Test
    @DisplayName("응답이 없으면 응답 대기 시간 뒤 UNKNOWN이고, 상대는 처리했으므로 조회거래가 0000을 돌려준다")
    void send_noResponse_isUnknownAndInquiryFindsApproved() throws Exception {
        InterbankSocketClient client = client();
        InterbankRequest request = request("1234567890123");
        setFault("mode=NO_RESPONSE");

        long start = System.nanoTime();
        InterbankReply sent = client.send(request);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        setFault("mode=NONE");
        InterbankReply inquired = client.inquire(request);

        assertThat(sent.outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
        assertThat(sent.responseCode()).isNull();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(RESPONSE_TIMEOUT.toMillis());
        assertThat(inquired.outcome()).isEqualTo(InterbankOutcome.APPROVED);
        assertThat(inquired.payeeName()).isEqualTo("김민수");
    }

    @Test
    @DisplayName("중복응답(같은 응답 두 번)은 첫 응답만 읽어 결과는 한 번만 나온다")
    void send_duplicateResponse_readsFirstOnly() throws Exception {
        setFault("mode=DUPLICATE");

        InterbankReply reply = client().send(request("1234567890123"));

        assertThat(reply.outcome()).isEqualTo(InterbankOutcome.APPROVED);
    }

    @ParameterizedTest(name = "계좌 {0}… → 조회 {1}")
    @CsvSource({"1001000000000, ACCOUNT_NOT_FOUND", "1002000000000, ACCOUNT_UNAVAILABLE"})
    @DisplayName("조회거래는 원거래가 거절된 사유를 그대로 돌려준다")
    void inquire_rejectedOriginal_returnsOriginalReason(String accountNumber, InterbankOutcome expected) {
        InterbankSocketClient client = client();
        InterbankRequest request = request(accountNumber);
        client.send(request);

        assertThat(client.inquire(request).outcome()).isEqualTo(expected);
    }

    @Test
    @DisplayName("상대에 원거래 기록이 없으면(3001) 처리되지 않은 것으로 확정한다")
    void inquire_originalNotReceived_isNotProcessed() {
        InterbankReply reply = client().inquire(request("1234567890123"));

        assertThat(reply.outcome()).isEqualTo(InterbankOutcome.NOT_PROCESSED);
        assertThat(reply.responseCode()).isEqualTo("3001");
    }

    @Test
    @DisplayName("9999로 거절돼 장부에 없는 원거래도 조회하면 3001 — 처리되지 않음으로 확정한다")
    void inquire_afterSystemError_isNotProcessed() {
        InterbankSocketClient client = client();
        InterbankRequest request = request("9999000000000");
        client.send(request);

        assertThat(client.inquire(request).outcome()).isEqualTo(InterbankOutcome.NOT_PROCESSED);
    }

    @Test
    @DisplayName("연결조차 안 되면 보내지 못한 것이라 송신은 NOT_PROCESSED, 조회는 UNKNOWN이다")
    void connectionRefused_sendNotProcessed_inquiryUnknown() throws IOException {
        InterbankSocketClient client = client(closedPort(), 100);

        assertThat(client.send(request("1234567890123")).outcome()).isEqualTo(InterbankOutcome.NOT_PROCESSED);
        assertThat(client.inquire(request("1234567890123")).outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
    }

    @Test
    @DisplayName("통신 실패가 쌓이면 서킷이 열려 isAvailable()이 false가 되고, 열린 동안은 연결을 시도하지 않는다")
    void circuitOpens_afterFailures() throws Exception {
        InterbankSocketClient client = client(server.port(), 2);
        setFault("mode=NO_RESPONSE");
        client.send(request("1234567890123"));
        client.send(request("1234567890123"));

        setFault("mode=NONE");
        long start = System.nanoTime();
        InterbankReply reply = client.send(request("1234567890123"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(client.isAvailable()).isFalse();
        assertThat(reply.outcome()).isEqualTo(InterbankOutcome.NOT_PROCESSED);
        assertThat(elapsedMillis).isLessThan(RESPONSE_TIMEOUT.toMillis());
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
