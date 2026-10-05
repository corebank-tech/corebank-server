package com.shinhan.mockexternal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.transfer.adapter.out.external.ExternalMessage;
import com.shinhan.corebank.transfer.adapter.out.external.ExternalMessageCodec;
import com.shinhan.corebank.transfer.adapter.out.external.ResponseCode;
import com.shinhan.corebank.transfer.adapter.out.external.TransactionCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MockExternalServer 모의 대외기관 소켓 테스트")
class MockExternalServerTest {

    private static final int READ_TIMEOUT_MILLIS = 2_000;
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 23, 10, 30, 15);

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

    private String nextSerial() {
        return "20261023WB" + String.format("%010d", ++serialSeq);
    }

    private static ExternalMessage request(TransactionCode code, String serial, String accountNumber) {
        return ExternalMessage.request(code, SENT_AT, "004", serial, accountNumber, 50_000L, "홍길동");
    }

    private ExternalMessage send(ExternalMessage request) throws IOException {
        try (Socket socket = connect()) {
            socket.getOutputStream().write(ExternalMessageCodec.encode(request));
            return ExternalMessageCodec.decode(readMessage(socket.getInputStream()));
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", server.port());
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        return socket;
    }

    private static byte[] readMessage(InputStream in) throws IOException {
        byte[] message = in.readNBytes(ExternalMessageCodec.MESSAGE_LENGTH);
        assertThat(message).hasSize(ExternalMessageCodec.MESSAGE_LENGTH);
        return message;
    }

    private HttpResponse<String> setFault(String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.adminPort() + "/faults?" + query))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    @DisplayName("이체(TRF001)")
    class TransferTest {

        @Test
        @DisplayName("정상 계좌면 0000과 수취인 이름을 돌려주고, 요청의 일련번호·본문을 그대로 싣는다")
        void approves() throws IOException {
            ExternalMessage request = request(TransactionCode.TRANSFER, nextSerial(), "1234567890123");

            ExternalMessage response = send(request);

            assertThat(response.responseCode()).isEqualTo(ResponseCode.APPROVED);
            assertThat(response.payeeName()).isEqualTo(MockBank.PAYEE_NAME);
            assertThat(response.serialNumber()).isEqualTo(request.serialNumber());
            assertThat(response.amount()).isEqualTo(request.amount());
            assertThat(response.memo()).isEqualTo(request.memo());
        }

        @ParameterizedTest(name = "계좌 {0}… → {1}")
        @CsvSource({"1001000000001,ACCOUNT_NOT_FOUND", "1002000000001,ACCOUNT_UNAVAILABLE", "9999000000001,SYSTEM_ERROR"
        })
        @DisplayName("계좌번호 앞 4자리로 거절 응답을 골라 받는다")
        void rejectsByAccountPrefix(String accountNumber, ResponseCode expected) throws IOException {
            ExternalMessage response = send(request(TransactionCode.TRANSFER, nextSerial(), accountNumber));

            assertThat(response.responseCode()).isEqualTo(expected);
            assertThat(response.payeeName()).isNull();
        }

        @Test
        @DisplayName("같은 일련번호를 다시 보내면 재처리하지 않고 2001이다")
        void rejectsDuplicateSerial() throws IOException {
            String serial = nextSerial();
            send(request(TransactionCode.TRANSFER, serial, "1234567890123"));

            ExternalMessage second = send(request(TransactionCode.TRANSFER, serial, "1234567890123"));

            assertThat(second.responseCode()).isEqualTo(ResponseCode.DUPLICATE);
        }

        @Test
        @DisplayName("형식이 깨진 전문에는 1003으로 답하고 장부에 남기지 않는다")
        void answersMalformedWithoutRecording() throws IOException {
            String serial = nextSerial();
            byte[] broken = ExternalMessageCodec.encode(request(TransactionCode.TRANSFER, serial, "1234567890123"));
            broken[80] = ' '; // 금액 칸(71~83) 한가운데

            ExternalMessage response;
            try (Socket socket = connect()) {
                socket.getOutputStream().write(broken);
                response = ExternalMessageCodec.decode(readMessage(socket.getInputStream()));
            }

            assertThat(response.responseCode()).isEqualTo(ResponseCode.MALFORMED_MESSAGE);
            assertThat(response.serialNumber()).isEqualTo(serial);
            assertThat(send(request(TransactionCode.INQUIRY, serial, "1234567890123"))
                            .responseCode())
                    .isEqualTo(ResponseCode.ORIGINAL_NOT_RECEIVED);
        }
    }

    @Nested
    @DisplayName("조회거래(INQ001)")
    class InquiryTest {

        @ParameterizedTest(name = "원거래 계좌 {0} → 조회 {1}")
        @CsvSource({
            "1234567890123,APPROVED",
            "1001000000001,ACCOUNT_NOT_FOUND",
            "1002000000001,ACCOUNT_UNAVAILABLE",
            "9999000000001,ORIGINAL_NOT_RECEIVED"
        })
        @DisplayName("원거래 결과를 돌려준다. 시스템 오류로 처리 못 한 원거래는 기록이 없어 3001이다")
        void returnsOriginalResult(String accountNumber, ResponseCode expected) throws IOException {
            String serial = nextSerial();
            send(request(TransactionCode.TRANSFER, serial, accountNumber));

            ExternalMessage response = send(request(TransactionCode.INQUIRY, serial, accountNumber));

            assertThat(response.responseCode()).isEqualTo(expected);
        }

        @Test
        @DisplayName("받은 적 없는 일련번호는 3001이다")
        void unknownSerial() throws IOException {
            ExternalMessage response = send(request(TransactionCode.INQUIRY, nextSerial(), "1234567890123"));

            assertThat(response.responseCode()).isEqualTo(ResponseCode.ORIGINAL_NOT_RECEIVED);
        }
    }

    @Nested
    @DisplayName("장애 스위치 — 관리 HTTP로 켠다")
    class FaultTest {

        @Test
        @DisplayName("지연: 설정한 시간만큼 늦게 응답한다")
        void delays() throws Exception {
            setFault("mode=DELAY&delayMs=300");

            long started = System.nanoTime();
            ExternalMessage response = send(request(TransactionCode.TRANSFER, nextSerial(), "1234567890123"));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertThat(response.responseCode()).isEqualTo(ResponseCode.APPROVED);
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(300);
        }

        @Test
        @DisplayName("무응답: 응답은 오지 않지만 처리는 돼 있어서, 스위치를 끄고 조회하면 0000이다")
        void processesButDoesNotRespond() throws Exception {
            setFault("mode=NO_RESPONSE");
            String serial = nextSerial();

            try (Socket socket = connect()) {
                socket.setSoTimeout(500);
                socket.getOutputStream()
                        .write(ExternalMessageCodec.encode(request(TransactionCode.TRANSFER, serial, "1234567890123")));
                assertThatThrownBy(() -> socket.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
            }

            setFault("mode=NONE");
            ExternalMessage inquiry = send(request(TransactionCode.INQUIRY, serial, "1234567890123"));
            assertThat(inquiry.responseCode()).isEqualTo(ResponseCode.APPROVED);
        }

        @Test
        @DisplayName("중복응답: 한 연결에 같은 응답 124바이트가 두 번 온다")
        void respondsTwice() throws Exception {
            setFault("mode=DUPLICATE");

            try (Socket socket = connect()) {
                socket.getOutputStream()
                        .write(ExternalMessageCodec.encode(
                                request(TransactionCode.TRANSFER, nextSerial(), "1234567890123")));
                byte[] first = readMessage(socket.getInputStream());
                byte[] second = readMessage(socket.getInputStream());

                assertThat(second).isEqualTo(first);
                assertThat(ExternalMessageCodec.decode(first).responseCode()).isEqualTo(ResponseCode.APPROVED);
            }
        }

        @Test
        @DisplayName("알 수 없는 모드는 400으로 거부하고 스위치를 바꾸지 않는다")
        void rejectsUnknownMode() throws Exception {
            HttpResponse<String> response = setFault("mode=EXPLODE");

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(send(request(TransactionCode.TRANSFER, nextSerial(), "1234567890123"))
                            .responseCode())
                    .isEqualTo(ResponseCode.APPROVED);
        }

        // 음수 지연은 sleep이 바로 리턴해 실제로는 NONE처럼 돌면서 스위치에는 DELAY로 보인다.
        @Test
        @DisplayName("음수 지연은 400으로 거부하고 스위치를 바꾸지 않는다")
        void rejectsNegativeDelay() throws Exception {
            HttpResponse<String> response = setFault("mode=DELAY&delayMs=-1");

            assertThat(response.statusCode()).isEqualTo(400);
            HttpResponse<String> current = http.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.adminPort() + "/faults"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(current.body()).isEqualTo("NONE delayMs=0");
        }

        @Test
        @DisplayName("health는 200 OK다")
        void health() throws Exception {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.adminPort() + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("OK");
        }
    }
}
