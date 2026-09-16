package com.shinhan.corebank.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LoginApiIntegrationTest.TestSessionController.class)
@DisplayName("로그인 API 세션 통합 테스트")
class LoginApiIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "login-api-user";
    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String WRONG_PASSWORD = "WrongPassword1!";

    @LocalServerPort
    private int serverPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private CookieManager cookieManager;
    private HttpClient httpClient;
    private Long customerId;

    @BeforeEach
    void setUp() {
        deleteTestData();
        customerId = insertCustomer();

        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("로그인 성공 시 세션 ID를 변경하고 보호 API에서 인증정보를 복원한다")
    void createsAuthenticatedSessionAndChangesSessionId() throws Exception {
        HttpResponse<String> preLoginResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/products/test-session")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(preLoginResponse.statusCode()).isEqualTo(200);

        String previousSessionId = currentSessionId();
        String previousCsrfToken = currentCsrfToken();
        Instant requestedAt = Instant.now();

        HttpResponse<String> loginResponse =
                httpClient.send(loginRequest(RAW_PASSWORD), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Instant respondedAt = Instant.now();
        String currentSessionId = currentSessionId();
        JsonNode body = objectMapper.readTree(loginResponse.body());

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(loginResponse.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> {
                    assertThat(cookie).contains("JSESSIONID=");
                    assertThat(cookie).containsIgnoringCase("HttpOnly");
                    assertThat(cookie).containsIgnoringCase("SameSite=Lax");
                })
                .anySatisfy(cookie -> {
                    assertThat(cookie).contains("XSRF-TOKEN=");
                    assertThat(cookie).containsIgnoringCase("Path=/");
                    assertThat(cookie).containsIgnoringCase("SameSite=Lax");
                    assertThat(cookie).doesNotContain("HttpOnly");
                });
        assertThat(currentSessionId).isNotEqualTo(previousSessionId);
        assertThat(currentCsrfToken()).isNotEqualTo(previousCsrfToken);
        assertThat(body.get("code").asText()).isEqualTo("0000");
        assertThat(body.get("data").get("customerId").asLong()).isEqualTo(customerId);
        assertThat(body.get("data").get("userName").asText()).isEqualTo("로그인고객");

        OffsetDateTime sessionExpiresAt =
                OffsetDateTime.parse(body.get("data").get("sessionExpiresAt").asText());

        assertThat(sessionExpiresAt.toInstant())
                .isAfterOrEqualTo(requestedAt.plusSeconds(600))
                .isBeforeOrEqualTo(respondedAt.plusSeconds(600));
        assertThat(loginResponse.body()).doesNotContain(RAW_PASSWORD).doesNotContain(currentSessionId);

        HttpResponse<String> protectedResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/accounts")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode protectedBody = objectMapper.readTree(protectedResponse.body());

        assertThat(protectedResponse.statusCode()).isEqualTo(200);
        assertThat(protectedBody.get("code").asText()).isEqualTo("0000");
    }

    @Test
    @DisplayName("비밀번호 불일치 시 횟수 데이터를 반환하고 세션을 생성하지 않는다")
    void doesNotCreateSessionWhenPasswordIsInvalid() throws Exception {
        HttpResponse<String> response = httpClient.send(
                loginRequest(WRONG_PASSWORD), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Set-Cookie"))
                .noneMatch(cookie -> cookie.contains("JSESSIONID="))
                .noneMatch(cookie -> cookie.contains("XSRF-TOKEN="));
        assertThat(cookieManager.getCookieStore().getCookies())
                .noneMatch(cookie -> "JSESSIONID".equals(cookie.getName()));
        assertThat(cookieManager.getCookieStore().getCookies())
                .noneMatch(cookie -> "XSRF-TOKEN".equals(cookie.getName()));
        assertThat(body.get("code").asText()).isEqualTo("ATH0101");
        assertThat(body.get("data").get("errorCount").asInt()).isEqualTo(1);
        assertThat(body.get("data").get("remainingAttempts").asInt()).isEqualTo(4);
        assertThat(response.body()).doesNotContain(RAW_PASSWORD).doesNotContain(WRONG_PASSWORD);

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT login_failure_count FROM customer WHERE customer_id = ?", Integer.class, customerId);

        assertThat(failureCount).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 아이디는 비밀번호 불일치와 동일한 데이터를 반환한다 (REQ-AUTH-023)")
    void returnsSameFailureShapeForNonexistentUserId() throws Exception {
        HttpResponse<String> nonexistentUserResponse = httpClient.send(
                loginRequest("nonexistent-user-id", WRONG_PASSWORD),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> wrongPasswordResponse = httpClient.send(
                loginRequest(WRONG_PASSWORD), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode nonexistentUserBody = objectMapper.readTree(nonexistentUserResponse.body());
        JsonNode wrongPasswordBody = objectMapper.readTree(wrongPasswordResponse.body());

        assertThat(nonexistentUserResponse.statusCode()).isEqualTo(401);
        assertThat(wrongPasswordResponse.statusCode()).isEqualTo(401);
        assertThat(nonexistentUserBody.get("code").asText())
                .isEqualTo(wrongPasswordBody.get("code").asText());
        assertThat(nonexistentUserBody.get("data")).isEqualTo(wrongPasswordBody.get("data"));
    }

    @Test
    @DisplayName("로그아웃 후 세션과 CSRF 토큰을 폐기하고 재로그인 시 모두 재발급한다")
    void invalidatesSessionAndAuthenticationOnLogout() throws Exception {
        HttpResponse<String> loginResponse =
                httpClient.send(loginRequest(RAW_PASSWORD), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        String loggedInSessionId = currentSessionId();
        String csrfToken = currentCsrfToken();

        HttpResponse<String> logoutResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/auth/logout"))
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode logoutBody = objectMapper.readTree(logoutResponse.body());

        assertThat(logoutResponse.statusCode()).isEqualTo(200);
        assertThat(logoutBody.get("code").asText()).isEqualTo("0000");
        assertThat(logoutBody.get("message").asText()).isEqualTo("로그아웃되었습니다.");
        assertThat(logoutBody.get("data").isNull()).isTrue();
        assertThat(logoutResponse.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(logoutResponse.headers().allValues("Set-Cookie")).anySatisfy(cookie -> {
            assertThat(cookie).contains("JSESSIONID=");
            assertThat(cookie).containsIgnoringCase("Expires=");
        });
        assertThat(cookieManager.getCookieStore().getCookies())
                .noneMatch(cookie -> "JSESSIONID".equals(cookie.getName()));
        assertThat(cookieManager.getCookieStore().getCookies())
                .noneMatch(cookie -> "XSRF-TOKEN".equals(cookie.getName()));

        HttpResponse<String> protectedResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/accounts")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode protectedBody = objectMapper.readTree(protectedResponse.body());

        assertThat(protectedResponse.statusCode()).isEqualTo(401);
        assertThat(protectedBody.get("code").asText()).isEqualTo("CMN0101");

        HttpResponse<String> reloginResponse =
                httpClient.send(loginRequest(RAW_PASSWORD), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(reloginResponse.statusCode()).isEqualTo(200);
        assertThat(currentSessionId()).isNotEqualTo(loggedInSessionId);
        assertThat(currentCsrfToken()).isNotEqualTo(csrfToken);
    }

    private HttpRequest loginRequest(String password) {
        return loginRequest(USER_ID, password);
    }

    private HttpRequest loginRequest(String userId, String password) {
        String requestBody =
                """
                {
                  "userId": "%s",
                  "password": "%s"
                }
                """
                        .formatted(userId, password);

        return HttpRequest.newBuilder(uri("/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Forwarded-For", "203.0.113.10")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:%d/api/v1%s".formatted(serverPort, path));
    }

    private String currentSessionId() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("JSESSIONID 쿠키가 존재하지 않습니다."));
    }

    private String currentCsrfToken() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 존재하지 않습니다."));
    }

    private Long insertCustomer() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    NOW(6), NOW(6), NOW(6)
                )
                """,
                USER_ID,
                passwordHash,
                "로그인고객",
                "1990-01-01",
                "login-api@example.com",
                "01012345678");

        return jdbcTemplate.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, USER_ID);
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                """
                DELETE FROM audit_log
                WHERE customer_id IN (
                    SELECT customer_id
                    FROM customer
                    WHERE user_id = ?
                )
                """,
                USER_ID);

        jdbcTemplate.update("DELETE FROM customer WHERE user_id = ?", USER_ID);
    }

    // 로그인 전 세션 ID를 발급하기 위한 테스트 전용 공개 API
    @RestController
    static class TestSessionController {

        @GetMapping("/products/test-session")
        void createSession(HttpServletRequest request, CsrfToken csrfToken) {
            request.getSession(true);
            csrfToken.getToken();
        }
    }
}
