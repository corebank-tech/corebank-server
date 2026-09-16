package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("로그인 감사 로그 MySQL 통합 테스트")
class LoginAuditPersistenceIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "login-audit-user";
    private static final String EMAIL = "login-audit@example.com";
    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String WRONG_PASSWORD = "WrongPassword1!";
    private static final String REQUEST_IP = "192.168.0.10";

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        deleteTestData();
        passwordHash = passwordEncoder.encode(RAW_PASSWORD);
        customerId = insertCustomer();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    // 로그인 성공 감사가 LOGIN 이벤트로 실제 저장되는지 검증
    @Test
    @DisplayName("로그인 성공은 민감정보 없이 LOGIN 성공 감사로 저장된다")
    void persistsSuccessfulLoginAudit() {
        LoginResult result = loginUseCase.login(new LoginCommand(USER_ID, RAW_PASSWORD, REQUEST_IP));

        assertThat(result.customerId()).isEqualTo(customerId);

        PersistedAudit audit = findLatestAudit();

        assertThat(audit.eventType()).isEqualTo("LOGIN");
        assertThat(audit.result()).isEqualTo("SUCCESS");
        assertThat(audit.reason()).isEqualTo("SUCCESS");
        assertSensitiveDataNotRecorded(audit.detail());
    }

    // 로그인 실패 감사도 고객 상태 커밋 후 별도 행으로 저장되는지 검증
    @Test
    @DisplayName("로그인 실패는 민감정보 없이 LOGIN 실패 감사로 저장된다")
    void persistsFailedLoginAudit() {
        LoginFailedException exception = catchThrowableOfType(
                () -> loginUseCase.login(new LoginCommand(USER_ID, WRONG_PASSWORD, REQUEST_IP)),
                LoginFailedException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);

        PersistedAudit audit = findLatestAudit();

        assertThat(audit.eventType()).isEqualTo("LOGIN");
        assertThat(audit.result()).isEqualTo("FAILURE");
        assertThat(audit.reason()).isEqualTo("INVALID_CREDENTIALS");
        assertSensitiveDataNotRecorded(audit.detail());
    }

    private Long insertCustomer() {
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
                "감사테스트",
                "1990-01-01",
                EMAIL,
                "01012345678");

        return jdbcTemplate.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, USER_ID);
    }

    private PersistedAudit findLatestAudit() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    event_type,
                    result,
                    JSON_UNQUOTE(JSON_EXTRACT(detail, '$.reason')) AS reason,
                    CAST(detail AS CHAR) AS detail
                FROM audit_log
                WHERE customer_id = ?
                ORDER BY audit_log_id DESC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> new PersistedAudit(
                        resultSet.getString("event_type"),
                        resultSet.getString("result"),
                        resultSet.getString("reason"),
                        resultSet.getString("detail")),
                customerId);
    }

    private void assertSensitiveDataNotRecorded(String detail) {
        assertThat(detail)
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain(WRONG_PASSWORD)
                .doesNotContain(passwordHash);
        assertThat(detail.toLowerCase())
                .doesNotContain("password")
                .doesNotContain("hash")
                .doesNotContain("sessionid")
                .doesNotContain("session_id");
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

    // 감사 로그의 보안 검증에 필요한 저장 결과
    private record PersistedAudit(String eventType, String result, String reason, String detail) {}
}
