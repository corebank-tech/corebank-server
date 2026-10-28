package com.shinhan.infoapi.adapter.in.http;

import com.shinhan.infoapi.application.port.in.MartQueryUseCase;
import com.shinhan.infoapi.application.port.out.MartReadException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import tools.jackson.databind.ObjectMapper;

// 온프레미스 정보계 API(PH-77 ②). WAS가 VPN 안쪽에서만 부르고, 공유 비밀 헤더가 맞을 때만 응답한다.
public final class InfoApiServer {

    public static final String SECRET_HEADER = "X-Info-Api-Secret";

    private static final System.Logger LOG = System.getLogger(InfoApiServer.class.getName());
    private static final int WORKER_THREADS = 4;
    private static final int STOP_DELAY_SECONDS = 2;

    private final HttpServer server;
    private final MartQueryUseCase query;
    private final byte[] secret;
    private final ObjectMapper json = new ObjectMapper();

    public InfoApiServer(MartQueryUseCase query, String sharedSecret, int port) throws IOException {
        this.query = query;
        this.secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/info/deposit-balances", this::depositBalances);
        server.createContext("/info/product-subscriptions", this::productSubscriptions);
        server.setExecutor(Executors.newFixedThreadPool(WORKER_THREADS));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(STOP_DELAY_SECONDS);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void depositBalances(HttpExchange exchange) throws IOException {
        handle(exchange, params -> query.depositBalances(date(params, "from"), date(params, "to")));
    }

    private void productSubscriptions(HttpExchange exchange) throws IOException {
        handle(exchange, params -> query.productSubscriptions(month(params)));
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Map<String, String> params);
    }

    // 인증 → 메서드 → 처리 순서다. 인증에 실패하면 어떤 경로가 있는지도 알려 주지 않는다.
    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        long started = System.nanoTime();
        int status = 500;
        try {
            if (!authenticated(exchange)) {
                status = 401;
                write(exchange, status, errorBody(status));
            } else if (!"GET".equals(exchange.getRequestMethod())) {
                status = 405;
                write(exchange, status, errorBody(status));
            } else {
                Object data = handler.handle(parse(exchange.getRequestURI().getRawQuery()));
                status = 200;
                write(exchange, status, new Envelope("0000", "정상 처리되었습니다.", data));
            }
        } catch (IllegalArgumentException e) {
            status = 400;
            write(exchange, status, errorBody(status));
        } catch (MartReadException e) {
            status = 500;
            LOG.log(System.Logger.Level.ERROR, e.getMessage(), e);
            write(exchange, status, errorBody(status));
        } catch (RuntimeException e) {
            status = 500;
            LOG.log(System.Logger.Level.ERROR, "정보계 API 처리 중 예기치 못한 오류", e);
            write(exchange, status, errorBody(status));
        } finally {
            exchange.close();
            long millis = (System.nanoTime() - started) / 1_000_000;
            LOG.log(
                    System.Logger.Level.INFO,
                    "{0} {1} -> {2} ({3}ms)",
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    status,
                    millis);
        }
    }

    // 상수 시간 비교다. 앞부분이 맞을수록 오래 걸리는 equals 와 달리 응답 시간으로 비밀을 추측할 수 없다.
    private boolean authenticated(HttpExchange exchange) {
        String provided = exchange.getRequestHeaders().getFirst(SECRET_HEADER);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(secret, actual);
    }

    private static Map<String, String> parse(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private static LocalDate date(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " 형식이 올바르지 않습니다.", e);
        }
    }

    private static YearMonth month(Map<String, String> params) {
        String value = params.get("month");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month 형식이 올바르지 않습니다.", e);
        }
    }

    private void write(HttpExchange exchange, int status, Envelope body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static Envelope errorBody(int status) {
        return new Envelope(String.valueOf(status), message(status), null);
    }

    private static String message(int status) {
        return switch (status) {
            case 400 -> "요청 형식이 올바르지 않습니다.";
            case 401 -> "인증에 실패했습니다.";
            case 405 -> "허용되지 않는 메서드입니다.";
            default -> "정보계 API 처리 중 오류가 발생했습니다.";
        };
    }

    private record Envelope(String code, String message, Object data) {}
}
