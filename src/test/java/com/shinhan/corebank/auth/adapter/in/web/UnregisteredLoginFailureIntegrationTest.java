package com.shinhan.corebank.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.adapter.out.security.LoginIdBlindIndex;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.application.port.out.UnregisteredLoginFailurePort;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 없는 아이디도 가입 계정과 같은 기준(아이디)·같은 순서로 잠겨 응답만으로 계정 존재를 가늠할 수 없는지 본다 (#900)
@AutoConfigureMockMvc
@DisplayName("미가입 아이디 로그인 실패 누적 통합 테스트")
class UnregisteredLoginFailureIntegrationTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않는 ph08 대역
    private static final String REGISTERED_USER_ID = "ph08real01";
    private static final String UNREGISTERED_USER_ID = "ph08ghost01";
    private static final String ROTATING_IP_USER_ID = "ph08ghost02";
    private static final String MIXED_CASE_USER_ID = "ph08ghost03";
    private static final String CONCURRENT_USER_ID = "ph08ghost04";
    private static final String EXPIRED_USER_ID = "ph08ghost05";
    private static final String HASHED_USER_ID = "ph08ghost06";
    private static final List<String> UNREGISTERED_USER_IDS = List.of(
            UNREGISTERED_USER_ID,
            ROTATING_IP_USER_ID,
            MIXED_CASE_USER_ID,
            CONCURRENT_USER_ID,
            EXPIRED_USER_ID,
            HASHED_USER_ID);

    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String WRONG_PASSWORD = "WrongPassword1!";
    // 문서용 대역(TEST-NET-2)이라 다른 테스트의 감사 로그와 섞이지 않는다
    private static final String REQUEST_IP = "198.51.100.10";
    private static final String TEST_IP_PATTERN = "198.51.100.%";
    private static final int CONCURRENT_REQUESTS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginIdBlindIndex loginIdBlindIndex;

    @Autowired
    private UnregisteredLoginFailurePort unregisteredLoginFailurePort;

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        deleteTestData();
        insertCustomer(REGISTERED_USER_ID);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("없는 아이디와 가입 아이디는 5회 실패하는 동안 같은 코드와 남은 횟수를 받는다")
    void unregisteredAndRegisteredFailuresLookTheSame() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            final int remainingAttempts = 5 - attempt;
            loginWithWrongPassword(REGISTERED_USER_ID, REQUEST_IP)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ATH0101"))
                    .andExpect(jsonPath("$.data.remainingAttempts").value(remainingAttempts));
            loginWithWrongPassword(UNREGISTERED_USER_ID, REQUEST_IP)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ATH0101"))
                    .andExpect(jsonPath("$.data.remainingAttempts").value(remainingAttempts));
        }

        loginWithWrongPassword(REGISTERED_USER_ID, REQUEST_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATH0102"));
        loginWithWrongPassword(UNREGISTERED_USER_ID, REQUEST_IP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATH0102"));

        // 잠긴 뒤에도 둘 다 계속 잠금으로 응답한다
        loginWithWrongPassword(UNREGISTERED_USER_ID, REQUEST_IP)
                .andExpect(jsonPath("$.code").value("ATH0102"));
        loginWithWrongPassword(REGISTERED_USER_ID, REQUEST_IP)
                .andExpect(jsonPath("$.code").value("ATH0102"));
    }

    @Test
    @DisplayName("IP를 바꿔 가며 한 번씩 실패해도 없는 아이디는 5회째에 잠금으로 응답한다")
    void rotatingIpDoesNotResetUnregisteredFailures() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            loginWithWrongPassword(ROTATING_IP_USER_ID, "198.51.100." + attempt)
                    .andExpect(jsonPath("$.code").value("ATH0101"))
                    .andExpect(jsonPath("$.data.remainingAttempts").value(5 - attempt));
        }

        loginWithWrongPassword(ROTATING_IP_USER_ID, "198.51.100.5")
                .andExpect(jsonPath("$.code").value("ATH0102"));
    }

    @Test
    @DisplayName("대소문자만 다른 아이디는 같은 카운터로 센다")
    void mixedCaseUserIdsShareOneCounter() throws Exception {
        final List<String> variants =
                List.of("PH08Ghost03", "ph08ghost03", "PH08GHOST03", "Ph08gHost03", "ph08GHOST03");

        for (int attempt = 0; attempt < 4; attempt++) {
            loginWithWrongPassword(variants.get(attempt), REQUEST_IP)
                    .andExpect(jsonPath("$.code").value("ATH0101"))
                    .andExpect(jsonPath("$.data.remainingAttempts").value(4 - attempt));
        }

        loginWithWrongPassword(variants.get(4), REQUEST_IP)
                .andExpect(jsonPath("$.code").value("ATH0102"));
    }

    @Test
    @DisplayName("ASCII가 아닌 아이디는 가입 여부와 관계없이 같은 400으로 거절한다")
    void nonAsciiUserIdIsRejectedRegardlessOfRegistration() throws Exception {
        // DB 정렬 규칙이 é를 e로 보므로 이 입력은 가입 아이디 ph08real01을 가리킨다
        final String registeredVariant = "ph08réal01";
        final String unregisteredVariant = "ph08ghóst01";

        loginWithWrongPassword(registeredVariant, REQUEST_IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        loginWithWrongPassword(unregisteredVariant, REQUEST_IP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("같은 없는 아이디로 동시에 5건이 들어와도 횟수가 하나씩 쌓여 1건만 잠금 응답을 받는다")
    void concurrentFailuresAreCountedOneByOne() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final List<Future<String>> results = new ArrayList<>();
        try {
            for (int request = 0; request < CONCURRENT_REQUESTS; request++) {
                results.add(executor.submit(() -> {
                    startLatch.await();
                    return failLoginAndGetErrorCode(CONCURRENT_USER_ID);
                }));
            }
            startLatch.countDown();

            final List<String> errorCodes = new ArrayList<>();
            for (Future<String> result : results) {
                errorCodes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(errorCodes).filteredOn("ATH0101"::equals).hasSize(4);
            assertThat(errorCodes).filteredOn("ATH0102"::equals).hasSize(1);
            assertThat(storedFailureCount(CONCURRENT_USER_ID)).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("만료된 기록에 실패가 들어오면 1부터 다시 세고 만료 시각을 30일 뒤로 옮긴다")
    void expiredRecordRestartsFromOne() {
        final LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                """
                INSERT INTO unregistered_login_failure (user_id_hash, failure_count, last_failed_at, expires_at)
                VALUES (?, 5, ?, ?)
                """,
                loginIdBlindIndex.hash(EXPIRED_USER_ID),
                now.minusDays(31),
                now.minusDays(1));

        final int failureCount = unregisteredLoginFailurePort.recordFailure(EXPIRED_USER_ID);

        assertThat(failureCount).isEqualTo(1);
        final LocalDateTime expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM unregistered_login_failure WHERE user_id_hash = ?",
                LocalDateTime.class,
                loginIdBlindIndex.hash(EXPIRED_USER_ID));
        assertThat(expiresAt).isAfter(now.plusDays(29));
    }

    @Test
    @DisplayName("아이디 원문은 저장하지 않고 같은 아이디는 같은 해시 한 행에 쌓인다")
    void storesOnlyHashOfUserId() {
        unregisteredLoginFailurePort.recordFailure(HASHED_USER_ID);
        unregisteredLoginFailurePort.recordFailure(HASHED_USER_ID);

        final List<String> storedHashes = jdbcTemplate.queryForList(
                "SELECT user_id_hash FROM unregistered_login_failure WHERE user_id_hash = ?",
                String.class,
                loginIdBlindIndex.hash(HASHED_USER_ID));
        assertThat(storedHashes).singleElement().satisfies(hash -> {
            assertThat(hash).matches("^[0-9a-f]{64}$");
            assertThat(hash).doesNotContain(HASHED_USER_ID);
        });
        assertThat(storedFailureCount(HASHED_USER_ID)).isEqualTo(2);
    }

    private ResultActions loginWithWrongPassword(String userId, String remoteIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\",\"password\":\"" + WRONG_PASSWORD + "\"}")
                .with(request -> {
                    request.setRemoteAddr(remoteIp);
                    return request;
                }));
    }

    private String failLoginAndGetErrorCode(String userId) {
        try {
            loginUseCase.login(new LoginCommand(userId, WRONG_PASSWORD, REQUEST_IP, CustomerRole.CUSTOMER));
            return "SUCCESS";
        } catch (LoginFailedException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    private int storedFailureCount(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_count FROM unregistered_login_failure WHERE user_id_hash = ?",
                Integer.class,
                loginIdBlindIndex.hash(userId));
    }

    private void insertCustomer(String userId) {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id, password_hash, user_name, birth_date, email, phone_number,
                    joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))
                """,
                userId,
                passwordEncoder.encode(RAW_PASSWORD),
                "미가입테스트",
                "1990-01-01",
                userId + "@example.com",
                "01090080001");
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                "DELETE FROM audit_log WHERE customer_id IN (SELECT customer_id FROM customer WHERE user_id = ?)",
                REGISTERED_USER_ID);
        jdbcTemplate.update("DELETE FROM audit_log WHERE customer_id IS NULL AND request_ip LIKE ?", TEST_IP_PATTERN);
        jdbcTemplate.update("DELETE FROM customer WHERE user_id = ?", REGISTERED_USER_ID);
        for (String userId : UNREGISTERED_USER_IDS) {
            jdbcTemplate.update(
                    "DELETE FROM unregistered_login_failure WHERE user_id_hash = ?", loginIdBlindIndex.hash(userId));
        }
    }
}
