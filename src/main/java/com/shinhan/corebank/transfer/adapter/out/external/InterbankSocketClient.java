package com.shinhan.corebank.transfer.adapter.out.external;

import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.config.InterbankProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 타행 전문 TCP 송신(전문 규격 §1). 응답 대기는 소켓 타임아웃으로 건다 — Future 취소로는 막힌 읽기를 못 끊는다.
// 계좌번호·전문 원문은 로그에 남기지 않는다(로그 마스킹은 12자리만 잡는다, #654).
@Slf4j
@Component
public class InterbankSocketClient implements InterbankGatewayPort {

    private static final int LENGTH_FIELD = 4;

    private final InterbankProperties properties;
    private final Clock clock;
    private final CircuitBreaker circuitBreaker;

    public InterbankSocketClient(InterbankProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        InterbankProperties.CircuitBreaker breaker = properties.circuitBreaker();
        this.circuitBreaker = CircuitBreaker.of(
                "interbank",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(breaker.failureRateThreshold())
                        .slidingWindowSize(breaker.slidingWindowSize())
                        .minimumNumberOfCalls(breaker.minimumNumberOfCalls())
                        .waitDurationInOpenState(breaker.waitDurationInOpenState())
                        .build());
    }

    @Override
    public boolean isAvailable() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN;
    }

    @Override
    public InterbankReply send(InterbankRequest request) {
        Exchange exchange = exchange(TransactionCode.TRANSFER, request);
        InterbankReply reply =
                switch (exchange) {
                    case Exchange.NotSent notSent -> InterbankReply.of(InterbankOutcome.NOT_PROCESSED, null);
                    case Exchange.NoReply noReply -> InterbankReply.of(InterbankOutcome.UNKNOWN, null);
                    case Exchange.Replied replied -> transferReply(replied.response());
                };
        log.info(
                "타행 송신 - transactionNumber={}, bankCode={}, responseCode={}, outcome={}",
                request.transactionNumber(),
                request.bankCode(),
                reply.responseCode(),
                reply.outcome());
        return reply;
    }

    @Override
    public InterbankReply inquire(InterbankRequest request) {
        Exchange exchange = exchange(TransactionCode.INQUIRY, request);
        // 조회를 못 보냈거나 답을 못 받았으면 원거래 결과는 여전히 모른다.
        InterbankReply reply = exchange instanceof Exchange.Replied replied
                ? inquiryReply(replied.response())
                : InterbankReply.of(InterbankOutcome.UNKNOWN, null);
        log.info(
                "타행 조회거래 - transactionNumber={}, bankCode={}, responseCode={}, outcome={}",
                request.transactionNumber(),
                request.bankCode(),
                reply.responseCode(),
                reply.outcome());
        return reply;
    }

    // 규격 §3 이체(TRF001) 열. 2001(중복)은 입금 여부를 알 수 없어 확정하지 않는다.
    private static InterbankReply transferReply(ExternalMessage response) {
        ResponseCode code = response.responseCode();
        return switch (code) {
            case APPROVED -> new InterbankReply(InterbankOutcome.APPROVED, response.payeeName(), code.code());
            case ACCOUNT_NOT_FOUND -> InterbankReply.of(InterbankOutcome.ACCOUNT_NOT_FOUND, code.code());
            case ACCOUNT_UNAVAILABLE -> InterbankReply.of(InterbankOutcome.ACCOUNT_UNAVAILABLE, code.code());
            case MALFORMED_MESSAGE, SYSTEM_ERROR -> InterbankReply.of(InterbankOutcome.NOT_PROCESSED, code.code());
            case DUPLICATE, ORIGINAL_NOT_RECEIVED -> InterbankReply.of(InterbankOutcome.UNKNOWN, code.code());
        };
    }

    // 규격 §3 조회거래(INQ001) 열. 1003·9999는 원거래가 아니라 조회 자체의 결과라 다시 조회한다.
    private static InterbankReply inquiryReply(ExternalMessage response) {
        ResponseCode code = response.responseCode();
        return switch (code) {
            case APPROVED -> new InterbankReply(InterbankOutcome.APPROVED, response.payeeName(), code.code());
            case ACCOUNT_NOT_FOUND -> InterbankReply.of(InterbankOutcome.ACCOUNT_NOT_FOUND, code.code());
            case ACCOUNT_UNAVAILABLE -> InterbankReply.of(InterbankOutcome.ACCOUNT_UNAVAILABLE, code.code());
            case ORIGINAL_NOT_RECEIVED -> InterbankReply.of(InterbankOutcome.NOT_PROCESSED, code.code());
            case MALFORMED_MESSAGE, SYSTEM_ERROR, DUPLICATE -> InterbankReply.of(InterbankOutcome.UNKNOWN, code.code());
        };
    }

    private Exchange exchange(TransactionCode transactionCode, InterbankRequest request) {
        byte[] encoded;
        try {
            encoded = ExternalMessageCodec.encode(ExternalMessage.request(
                    transactionCode,
                    LocalDateTime.now(clock),
                    request.bankCode(),
                    request.transactionNumber(),
                    request.accountNumber(),
                    request.amount(),
                    request.recipientMemo()));
        } catch (IllegalArgumentException e) {
            // 입력 검증을 지나 왔어도 전문을 만들 수 없으면 보내지 않는다(규격 §4 — 직렬화가 마지막 관문).
            log.error("타행 전문 생성 실패 - transactionNumber={}, cause={}", request.transactionNumber(), e.getMessage());
            return new Exchange.NotSent();
        }
        if (!circuitBreaker.tryAcquirePermission()) {
            return new Exchange.NotSent();
        }
        long start = System.nanoTime();
        Exchange exchange = exchangeOverSocket(encoded, request.transactionNumber(), transactionCode);
        long elapsed = System.nanoTime() - start;
        if (exchange instanceof Exchange.Replied) {
            circuitBreaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
        } else {
            circuitBreaker.onError(elapsed, TimeUnit.NANOSECONDS, new IOException(exchange.toString()));
        }
        return exchange;
    }

    private Exchange exchangeOverSocket(byte[] encoded, String serialNumber, TransactionCode transactionCode) {
        try (Socket socket = new Socket()) {
            try {
                socket.connect(new InetSocketAddress(properties.host(), properties.port()), (int)
                        properties.connectTimeout().toMillis());
            } catch (IOException e) {
                // 연결 전이라 상대 은행은 이 전문을 받지 못했다.
                log.warn("타행 연결 실패 - transactionNumber={}, cause={}", serialNumber, e.toString());
                return new Exchange.NotSent();
            }
            socket.getOutputStream().write(encoded);
            socket.getOutputStream().flush();
            byte[] response = readMessage(
                    socket, System.nanoTime() + properties.responseTimeout().toNanos());
            if (response == null) {
                return new Exchange.NoReply();
            }
            ExternalMessage decoded = ExternalMessageCodec.decode(response);
            // 다른 거래의 응답을 이 거래 결과로 반영하지 않는다.
            if (decoded.kind() != MessageKind.RESPONSE
                    || !serialNumber.equals(decoded.serialNumber())
                    || decoded.transactionCode() != transactionCode) {
                log.warn("타행 응답 불일치 - transactionNumber={}", serialNumber);
                return new Exchange.NoReply();
            }
            return new Exchange.Replied(decoded);
        } catch (IOException | IllegalArgumentException e) {
            // 보낸 뒤의 실패라 상대가 처리했을 수 있다 — 처리 불명이다.
            log.warn("타행 응답 수신 실패 - transactionNumber={}, cause={}", serialNumber, e.toString());
            return new Exchange.NoReply();
        }
    }

    // 다 받기 전에 끊기면(EOF) 받지 않은 것으로 본다(규격 §1).
    private static byte[] readMessage(Socket socket, long deadline) throws IOException {
        byte[] message = new byte[ExternalMessageCodec.MESSAGE_LENGTH];
        if (!readFully(socket, message, 0, LENGTH_FIELD, deadline)) {
            return null;
        }
        int bodyLength = Integer.parseInt(new String(message, 0, LENGTH_FIELD, StandardCharsets.US_ASCII));
        if (LENGTH_FIELD + bodyLength != ExternalMessageCodec.MESSAGE_LENGTH) {
            return null;
        }
        return readFully(socket, message, LENGTH_FIELD, bodyLength, deadline) ? message : null;
    }

    // soTimeout은 read() 한 번의 상한이라, 남은 시간으로 매번 다시 걸어 전문 전체 수신을 응답 대기 시간 안에 묶는다.
    private static boolean readFully(Socket socket, byte[] buffer, int offset, int length, long deadline)
            throws IOException {
        InputStream in = socket.getInputStream();
        int end = offset + length;
        while (offset < end) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("응답 대기 시간 초과");
            }
            socket.setSoTimeout(soTimeoutMillis(remainingNanos));
            int read = in.read(buffer, offset, end - offset);
            if (read < 0) {
                return false;
            }
            offset += read;
        }
        return true;
    }

    // 올림한다. 내리면 1ms 미만이 남았을 때 마감 전에 끊고, 0은 soTimeout에서 무한 대기라 쓸 수 없다(#724).
    static int soTimeoutMillis(long remainingNanos) {
        return (int) TimeUnit.NANOSECONDS.toMillis(remainingNanos + 999_999);
    }

    private sealed interface Exchange {
        record NotSent() implements Exchange {}

        record NoReply() implements Exchange {}

        record Replied(ExternalMessage response) implements Exchange {}
    }
}
