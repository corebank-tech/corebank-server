package com.shinhan.mockexternal;

import com.shinhan.corebank.transfer.adapter.out.external.ExternalMessage;
import com.shinhan.corebank.transfer.adapter.out.external.ExternalMessageCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 모의 대외기관(타행) 서버 (PH-33-①, docs/phase2/fixed_length_message_spec.md).
 *
 * 전문 포트는 TCP 한 연결에 요청 1건·응답 1건이다. 4바이트 전문길이를 먼저 읽고 그 값만큼 더 읽는다.
 * 관리 포트는 HTTP로 장애 스위치를 바꾼다. 두 포트 모두 인터넷에 열지 않는다.
 *
 * 우리 은행 앱이 아니라 상대 기관이라 com.shinhan.corebank 밖에 두고, Spring 없이 JDK만으로 돈다.
 */
public final class MockExternalServer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(MockExternalServer.class.getName());
    private static final int LENGTH_FIELD = 4;
    private static final int MAX_DECLARED_LENGTH = 1024;

    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));
    private final MockBank bank = new MockBank();
    private final FaultSwitch faults = new FaultSwitch();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private final ServerSocket messageSocket;
    private final HttpServer admin;

    private MockExternalServer(String bind, int port, int adminPort) throws IOException {
        messageSocket = new ServerSocket();
        messageSocket.bind(new InetSocketAddress(bind, port));
        admin = HttpServer.create(new InetSocketAddress(bind, adminPort), 0);
        admin.createContext("/health", exchange -> reply(exchange, 200, "OK"));
        admin.createContext("/faults", this::handleFaults);
        admin.setExecutor(workers);
    }

    public static MockExternalServer start(String bind, int port, int adminPort) throws IOException {
        MockExternalServer server = new MockExternalServer(bind, port, adminPort);
        server.admin.start();
        Thread.ofPlatform().name("mock-external-accept").start(server::acceptLoop);
        LOG.log(Level.INFO, "모의 대외기관 시작 — 전문 {0}:{1}, 관리 {0}:{2}", bind, server.port(), server.adminPort());
        return server;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parse(args);
        MockExternalServer server = start(
                options.getOrDefault("bind", "127.0.0.1"),
                Integer.parseInt(options.getOrDefault("port", "9300")),
                Integer.parseInt(options.getOrDefault("admin-port", "9301")));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

    public int port() {
        return messageSocket.getLocalPort();
    }

    public int adminPort() {
        return admin.getAddress().getPort();
    }

    @Override
    public void close() {
        admin.stop(0);
        try {
            messageSocket.close();
        } catch (IOException ignored) {
            // 닫는 중 오류는 무시한다
        }
        openSockets.forEach(MockExternalServer::closeQuietly);
        workers.shutdownNow();
    }

    private void acceptLoop() {
        while (!messageSocket.isClosed()) {
            try {
                Socket socket = messageSocket.accept();
                openSockets.add(socket);
                workers.submit(() -> serve(socket));
            } catch (SocketException closed) {
                return;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "연결 수락 실패", e);
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            byte[] received = readMessage(in);
            byte[] response = respond(received);
            FaultSwitch.Fault fault = faults.current();
            switch (fault.mode()) {
                case NONE -> send(socket, response);
                case DELAY -> {
                    Thread.sleep(fault.delay());
                    send(socket, response);
                }
                case NO_RESPONSE -> in.transferTo(OutputStream.nullOutputStream());
                case DUPLICATE -> {
                    send(socket, response);
                    send(socket, response);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.DEBUG, "연결 종료", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            openSockets.remove(socket);
        }
    }

    // 전문길이가 숫자가 아니면 얼마나 더 읽을지 알 수 없어 받은 4바이트만으로 1003을 만든다.
    private static byte[] readMessage(InputStream in) throws IOException {
        byte[] lengthField = in.readNBytes(LENGTH_FIELD);
        String declared = new String(lengthField, StandardCharsets.US_ASCII);
        if (lengthField.length < LENGTH_FIELD || !declared.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return lengthField;
        }
        int bodyLength = Math.min(Integer.parseInt(declared), MAX_DECLARED_LENGTH);
        byte[] body = in.readNBytes(bodyLength);
        byte[] message = Arrays.copyOf(lengthField, LENGTH_FIELD + body.length);
        System.arraycopy(body, 0, message, LENGTH_FIELD, body.length);
        return message;
    }

    private byte[] respond(byte[] received) {
        LocalDateTime now = LocalDateTime.now(clock);
        ExternalMessage request;
        try {
            request = ExternalMessageCodec.decode(received);
        } catch (IllegalArgumentException malformed) {
            LOG.log(Level.INFO, "형식 오류 전문 수신 — 1003 응답: {0}", malformed.getMessage());
            return ExternalMessageCodec.malformedResponse(received, now);
        }
        ExternalMessage response = bank.handle(request, now);
        LOG.log(
                Level.INFO,
                "{0} {1} → {2} (장애 {3})",
                request.transactionCode().code(),
                request.serialNumber(),
                response.responseCode().code(),
                faults.current().mode());
        return ExternalMessageCodec.encode(response);
    }

    private static void send(Socket socket, byte[] response) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(response);
        out.flush();
    }

    // POST /faults?mode=DELAY&delayMs=3000 — 지금 값은 GET으로 본다.
    private void handleFaults(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            try {
                faults.set(
                        FaultSwitch.Mode.valueOf(query.getOrDefault("mode", "")),
                        Duration.ofMillis(Long.parseLong(query.getOrDefault("delayMs", "0"))));
            } catch (IllegalArgumentException e) {
                reply(exchange, 400, "mode는 NONE·DELAY·NO_RESPONSE·DUPLICATE, delayMs는 밀리초 정수다");
                return;
            }
            LOG.log(Level.INFO, "장애 스위치 변경 — {0}", faults.current());
        }
        FaultSwitch.Fault fault = faults.current();
        reply(exchange, 200, fault.mode() + " delayMs=" + fault.delay().toMillis());
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                options.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
            }
        }
        return options;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                query.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return query;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫는 중 오류는 무시한다
        }
    }
}
