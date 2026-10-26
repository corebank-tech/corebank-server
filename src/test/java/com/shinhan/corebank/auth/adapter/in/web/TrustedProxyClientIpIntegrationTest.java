package com.shinhan.corebank.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

// application-prod.yml의 forward-headers-strategy로 톰캣 RemoteIpValve가 ALB가 붙인 접속 IP와 scheme을 쓰는지 본다(#458).
// MockMvc는 톰캣 Valve를 거치지 않아 실제 포트로 요청한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TrustedProxyClientIpIntegrationTest.ForwardedRequestEchoController.class)
@DisplayName("신뢰 프록시 기준 접속 IP 통합 테스트")
class TrustedProxyClientIpIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "trusted-proxy-user";
    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String ALB_APPENDED_CLIENT_IP = "203.0.113.7";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // prod 파일을 정본으로 읽어, prod 설정이 되돌아가면 이 테스트가 깨지게 한다
    @DynamicPropertySource
    static void useProdForwardHeadersStrategy(DynamicPropertyRegistry registry) throws IOException {
        List<PropertySource<?>> prodProperties =
                new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml"));
        Object prodStrategy = prodProperties.getFirst().getProperty("server.forward-headers-strategy");
        registry.add("server.forward-headers-strategy", () -> String.valueOf(prodStrategy));
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Long customerId;

    @BeforeEach
    void setUp() {
        deleteTestData();
        customerId = insertCustomer();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("클라이언트가 X-Forwarded-For 앞에 넣은 값이 아니라 ALB가 붙인 IP를 로그인 감사와 최근 접속 IP에 남긴다")
    void recordsAlbAppendedIpOnLoginAudit() throws Exception {
        // given
        HttpRequest loginRequest = HttpRequest.newBuilder(uri("/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Forwarded-For", "1.2.3.4, " + ALB_APPENDED_CLIENT_IP)
                .POST(HttpRequest.BodyPublishers.ofString(
                        """
                        {"userId": "%s", "password": "%s"}
                        """
                                .formatted(USER_ID, RAW_PASSWORD),
                        StandardCharsets.UTF_8))
                .build();

        // when
        HttpResponse<String> response = HTTP_CLIENT.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT request_ip FROM audit_log WHERE customer_id = ? AND event_type = 'LOGIN'",
                        String.class,
                        customerId))
                .isEqualTo(ALB_APPENDED_CLIENT_IP);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_login_ip FROM customer WHERE customer_id = ?", String.class, customerId))
                .isEqualTo(ALB_APPENDED_CLIENT_IP);
    }

    @Test
    @DisplayName("클라이언트가 X-Forwarded-For에 사설 IP를 지어내도 ALB가 붙인 공인 IP에서 멈춘다")
    void stopsAtAlbAppendedIpWhenClientForgesPrivateIp() throws Exception {
        // given
        HttpRequest echoRequest = HttpRequest.newBuilder(uri("/products/test-forwarded"))
                .header("X-Forwarded-For", "10.0.0.9, " + ALB_APPENDED_CLIENT_IP)
                .GET()
                .build();

        // when
        ForwardedRequest forwardedRequest = echo(echoRequest);

        // then
        assertThat(forwardedRequest.remoteAddr()).isEqualTo(ALB_APPENDED_CLIENT_IP);
    }

    @Test
    @DisplayName("X-Forwarded-Proto가 https면 요청 scheme을 https로 인식한다")
    void recognizesHttpsFromForwardedProto() throws Exception {
        // given
        HttpRequest echoRequest = HttpRequest.newBuilder(uri("/products/test-forwarded"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();

        // when
        ForwardedRequest forwardedRequest = echo(echoRequest);

        // then
        assertThat(forwardedRequest.scheme()).isEqualTo("https");
    }

    private ForwardedRequest echo(HttpRequest echoRequest) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(echoRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.treeToValue(objectMapper.readTree(response.body()).get("data"), ForwardedRequest.class);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:%d/api/v1%s".formatted(serverPort, path));
    }

    private Long insertCustomer() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id, password_hash, user_name, birth_date, email, phone_number,
                    joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))
                """,
                USER_ID,
                passwordEncoder.encode(RAW_PASSWORD),
                "프록시고객",
                "1990-01-01",
                "trusted-proxy@example.com",
                "01098765432");
        return jdbcTemplate.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, USER_ID);
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                "DELETE FROM audit_log WHERE customer_id IN (SELECT customer_id FROM customer WHERE user_id = ?)",
                USER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE user_id = ?", USER_ID);
    }

    record ForwardedRequest(String remoteAddr, String scheme) {}

    // Valve가 바꾼 원격 주소와 scheme을 그대로 돌려주는 테스트 전용 공개 API
    @RestController
    static class ForwardedRequestEchoController {

        @GetMapping("/products/test-forwarded")
        ApiResponse<ForwardedRequest> echo(HttpServletRequest request) {
            return ApiResponse.success(new ForwardedRequest(request.getRemoteAddr(), request.getScheme()));
        }
    }
}
