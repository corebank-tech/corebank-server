package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

// 서로 메모리를 공유하지 않는 WAS JVM 두 개에 동일 쿠키를 보내 JDBC 세션을 검증한다.
class DualWasJdbcSessionIntegrationTest extends IntegrationTestSupport {
    private static final long CUSTOMER_ID = 638900001L;
    private static final String USER = "session638dual";
    private static final String PASSWORD = "Session8!old";
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Autowired
    JdbcConnectionDetails database;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void sharesSessionsAcrossProcessesAndRevokesThemEverywhere() throws Exception {
        jdbc.update(
                """
                INSERT INTO customer (customer_id,user_id,password_hash,user_name,birth_date,email,phone_number,
                login_failure_count,account_locked,joined_at,created_at,updated_at)
                VALUES (?,?,?,?, '1990-01-01',?,?,0,FALSE,NOW(6),NOW(6),NOW(6))
                """,
                CUSTOMER_ID,
                USER,
                encoder.encode(PASSWORD),
                "세션통합",
                "session638dual@example.com",
                "01063890001");
        List<Process> processes = new ArrayList<>();
        try {
            int portA = freePort();
            Process a = startWas(portA, "A");
            processes.add(a);
            int portB = freePort();
            Process b = startWas(portB, "B");
            processes.add(b);
            awaitHealthy(portA, a);
            awaitHealthy(portB, b);

            String cookies = login(portA, PASSWORD);
            // 만료 직전의 정상 요청이 무조작 만료시각을 다시 10분 뒤로 연장한다.
            jdbc.update(
                    "UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = ?, EXPIRY_TIME = ? WHERE PRINCIPAL_NAME = ?",
                    System.currentTimeMillis() - 590_000,
                    System.currentTimeMillis() + 10_000,
                    Long.toString(CUSTOMER_ID));
            assertProtected(portB, cookies, 200);
            assertThat(jdbc.queryForObject(
                            "SELECT EXPIRY_TIME FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                            Long.class,
                            Long.toString(CUSTOMER_ID)))
                    .isGreaterThan(System.currentTimeMillis() + 580_000);
            assertThat(send(portB, "/auth/logout", "POST", "", cookies).statusCode())
                    .isEqualTo(200);
            assertProtected(portA, cookies, 401);
            assertProtected(portB, cookies, 401);

            String first = login(portA, PASSWORD);
            String second = login(portB, PASSWORD);
            var changed = send(
                    portB,
                    "/customers/me/password",
                    "PUT",
                    """
                    {"currentPassword":"Session8!old","newPassword":"Session9!new","newPasswordConfirm":"Session9!new"}
                    """,
                    first);
            assertThat(changed.statusCode()).withFailMessage(changed.body()).isEqualTo(200);
            assertProtected(portA, first, 401);
            assertProtected(portB, second, 401);

            String survives = login(portA, "Session9!new");
            stop(a);
            assertProtected(portB, survives, 200);
            // 만료 정리 스케줄러가 돌기 전에도 지난 세션을 인증에 쓰지 못한다.
            jdbc.update(
                    "UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = ?, EXPIRY_TIME = ? WHERE PRINCIPAL_NAME = ?",
                    System.currentTimeMillis() - 700_000,
                    System.currentTimeMillis() - 1,
                    Long.toString(CUSTOMER_ID));
            assertProtected(portB, survives, 401);
        } finally {
            for (Process process : processes) stop(process);
            jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Long.toString(CUSTOMER_ID));
            jdbc.update("DELETE FROM idempotency_key WHERE customer_id = ?", CUSTOMER_ID);
            jdbc.update("DELETE FROM audit_log WHERE customer_id = ?", CUSTOMER_ID);
            jdbc.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
        }
    }

    private Process startWas(int port, String name) throws Exception {
        Path directory = Path.of("build", "reports", "dual-was");
        Files.createDirectories(directory);
        List<String> arguments = List.of(
                "-Xmx256m",
                "-Dfile.encoding=UTF-8",
                "-Dspring.devtools.restart.enabled=false",
                "-cp",
                System.getProperty("corebank.test.application-classpath"),
                "com.shinhan.corebank.CorebankApplication",
                "--spring.profiles.active=test",
                "--server.port=" + port,
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.main.banner-mode=off");
        Path argumentFile = Files.createTempFile("corebank-session-", ".args");
        Files.writeString(
                argumentFile,
                arguments.stream()
                        .map(value -> "\"" + value.replace("\\", "/").replace("\"", "\\\"") + "\"")
                        .collect(java.util.stream.Collectors.joining(System.lineSeparator())),
                StandardCharsets.UTF_8);
        argumentFile.toFile().deleteOnExit();
        return new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + argumentFile)
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("was-" + name + ".log").toFile())
                .start();
    }

    private void awaitHealthy(int port, Process process) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline && process.isAlive()) {
            try {
                if (send(port, "/actuator/health", "GET", "", "").statusCode() == 200) return;
            } catch (java.io.IOException ignored) {
                // 기동 중 연결 거부는 제한된 준비 대기 구간에서만 재시도한다.
            }
            Thread.sleep(250);
        }
        throw new AssertionError("WAS did not become healthy; see build/reports/dual-was");
    }

    private String login(int port, String password) throws Exception {
        var response = send(
                port, "/auth/login", "POST", "{\"userId\":\"" + USER + "\",\"password\":\"" + password + "\"}", "");
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        // 로그인 시 CSRF 삭제·재발급이 함께 오므로 브라우저처럼 마지막 쿠키 값을 사용한다.
        var cookieValues = new LinkedHashMap<String, String>();
        response.headers().allValues("Set-Cookie").forEach(value -> {
            String pair = value.split(";", 2)[0];
            cookieValues.put(pair.substring(0, pair.indexOf('=')), pair);
        });
        String cookies = String.join("; ", cookieValues.values());
        assertThat(cookies).contains("JSESSIONID=").contains("XSRF-TOKEN=");
        return cookies;
    }

    private void assertProtected(int port, String cookies, int status) throws Exception {
        var response = send(port, "/customers/me", "GET", "", cookies);
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    }

    private HttpResponse<String> send(int port, String path, String method, String body, String cookies)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies);
            for (String cookie : cookies.split("; ")) {
                if (cookie.startsWith("XSRF-TOKEN="))
                    builder.header("X-XSRF-TOKEN", URLDecoder.decode(cookie.substring(11), StandardCharsets.UTF_8));
            }
        }
        return client.send(
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
