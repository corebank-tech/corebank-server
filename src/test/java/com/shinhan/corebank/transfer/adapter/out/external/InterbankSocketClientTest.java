package com.shinhan.corebank.transfer.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.config.InterbankProperties;
import com.shinhan.mockexternal.MockExternalServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    private static final Duration WAIT_IN_OPEN = Duration.ofMinutes(1);
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
        return client(port, minimumNumberOfCalls, CLOCK);
    }

    private InterbankSocketClient client(int port, int minimumNumberOfCalls, Clock clock) {
        return new InterbankSocketClient(
                new InterbankProperties(
                        "127.0.0.1",
                        port,
                        Duration.ofMillis(500),
                        RESPONSE_TIMEOUT,
                        new InterbankProperties.CircuitBreaker(
                                50, minimumNumberOfCalls, minimumNumberOfCalls, WAIT_IN_OPEN),
                        new InterbankProperties.Inquiry(Duration.ZERO, 20)),
                clock);
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
    @DisplayName("응답이 조금씩 나눠 와 전문 전체 수신이 응답 대기 시간을 넘으면 그때 끊고 UNKNOWN이다")
    void send_responseTrickledPastTimeout_isUnknown() throws Exception {
        // 조각 사이 간격(100ms)은 응답 대기(500ms)보다 짧고, 다 받는 데는 2.5초가 걸린다
        try (ServerSocket relay = trickleRelay(30, Duration.ofMillis(100))) {
            InterbankSocketClient client = client(relay.getLocalPort(), 100);
            long start = System.nanoTime();
            InterbankReply reply = client.send(request("1234567890123"));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertThat(reply.outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
            // 상한은 CI 지연을 감안하되 전체 수신 시간보다는 짧게 둔다
            assertThat(elapsedMillis).isBetween(RESPONSE_TIMEOUT.toMillis(), 2_000L);
        }
    }

    @ParameterizedTest(name = "계좌 {0}… → {1}")
    @CsvSource({
        "1234567890123, APPROVED, 0000",
        "1001000000000, ACCOUNT_NOT_FOUND, 1001",
        "1002000000000, ACCOUNT_UNAVAILABLE, 1002",
        "9999000000000, NOT_PROCESSED, 9999"
    })
    @DisplayName("수취인 조회(PYE001)는 0000이면 예금주를 받고, 1001·1002는 구분하고, 그 밖의 응답은 확인 못 함이다")
    void inquirePayee_mapsResponseCode(String accountNumber, InterbankOutcome expected, String responseCode) {
        InterbankReply reply = client().inquirePayee("004", accountNumber);

        assertThat(reply.outcome()).isEqualTo(expected);
        assertThat(reply.responseCode()).isEqualTo(responseCode);
        assertThat(reply.payeeName()).isEqualTo(expected == InterbankOutcome.APPROVED ? "김민수" : null);
    }

    @Test
    @DisplayName("수취인 조회에 응답이 없으면 UNKNOWN, 연결하지 못하면 NOT_PROCESSED다")
    void inquirePayee_noResponseOrNotSent() throws Exception {
        setFault("mode=NO_RESPONSE");
        InterbankReply noResponse = client().inquirePayee("004", "1234567890123");
        InterbankReply notSent = client(closedPort(), 100).inquirePayee("004", "1234567890123");

        assertThat(noResponse.outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
        assertThat(notSent.outcome()).isEqualTo(InterbankOutcome.NOT_PROCESSED);
    }

    // 내림하면 1ms 미만이 남았을 때 0이 되어 마감 전에 끊는다 — CI에서 499ms로 깨진 원인(#724)
    @ParameterizedTest(name = "남은 {0}ns → {1}ms")
    @CsvSource({"1, 1", "600000, 1", "1000000, 1", "1000001, 2", "499600000, 500"})
    @DisplayName("소켓 대기 시간은 남은 시간을 올림해 마감 전에 끝나지 않는다")
    void soTimeoutMillis_roundsUpSoReadNeverEndsBeforeDeadline(long remainingNanos, int expectedMillis) {
        assertThat(InterbankSocketClient.soTimeoutMillis(remainingNanos)).isEqualTo(expectedMillis);
    }

    // 요청은 모의 대외기관에 그대로 넘기고, 받은 응답은 chunks 조각으로 나눠 gap마다 흘려보낸다.
    private ServerSocket trickleRelay(int chunks, Duration gap) throws IOException {
        ServerSocket relay = new ServerSocket(0);
        Thread.ofVirtual().start(() -> {
            try (Socket inbound = relay.accept();
                    Socket upstream = new Socket("127.0.0.1", server.port())) {
                upstream.getOutputStream()
                        .write(inbound.getInputStream().readNBytes(ExternalMessageCodec.MESSAGE_LENGTH));
                byte[] response = upstream.getInputStream().readNBytes(ExternalMessageCodec.MESSAGE_LENGTH);
                int chunkSize = (response.length + chunks - 1) / chunks;
                for (int offset = 0; offset < response.length; offset += chunkSize) {
                    Thread.sleep(gap);
                    inbound.getOutputStream().write(response, offset, Math.min(chunkSize, response.length - offset));
                    inbound.getOutputStream().flush();
                }
            } catch (IOException | InterruptedException ignored) {
                // 클라이언트가 먼저 끊으면 쓰기가 실패한다 — 이 테스트가 기대하는 경우다
            }
        });
        return relay;
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

    @Test
    @DisplayName("연결 거부로 열린 서킷은 조회 송신 없이 대기 시간만 지나도 다음 이체를 시험 송신한다")
    void openCircuit_afterWaitDuration_letsNextTransferThrough() throws Exception {
        int port = server.port();
        server.close();
        MovableClock clock = new MovableClock();
        InterbankSocketClient client = client(port, 2, clock);
        client.send(request("1234567890123"));
        client.send(request("1234567890123"));
        assertThat(client.isAvailable()).isFalse();

        server = MockExternalServer.start("127.0.0.1", port, 0);
        clock.advance(WAIT_IN_OPEN.plusSeconds(1));

        assertThat(client.isAvailable()).isTrue();
        assertThat(client.send(request("1234567890123")).outcome()).isEqualTo(InterbankOutcome.APPROVED);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // 테스트가 바늘을 직접 돌리는 시계. 서킷의 OPEN 대기 시간을 실제로 기다리지 않는다.
    private static final class MovableClock extends Clock {

        private Instant now = Instant.now();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return CLOCK.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
