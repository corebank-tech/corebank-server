package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@DisplayName("로그인 성공 상태와 감사 로그 원자성 통합 테스트")
class LoginSuccessAuditAtomicityIntegrationTest
        extends IntegrationTestSupport {

    private static final String USER_ID = "login-atomic-user";
    private static final String EMAIL = "login-atomic@example.com";
    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String REQUEST_IP = "192.168.0.10";
    private static final LocalDateTime ORIGINAL_LOGIN_AT =
            LocalDateTime.of(2026, 8, 10, 9, 0);
    private static final String ORIGINAL_LOGIN_IP = "127.0.0.1";

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RecordLoginAuditPort recordLoginAuditPort;

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

    // 성공 감사 실패 시 로그인 성공 상태도 같은 트랜잭션에서 롤백
    @Test
    @DisplayName("성공 감사 저장에 실패하면 로그인 상태 변경도 롤백된다")
    void rollsBackLoginStateWhenSuccessAuditFails() {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(recordLoginAuditPort)
                .record(
                        customerId,
                        REQUEST_IP,
                        true,
                        LoginAuditReason.SUCCESS
                );

        assertThatThrownBy(() -> loginUseCase.login(
                new LoginCommand(
                        USER_ID,
                        RAW_PASSWORD,
                        REQUEST_IP
                )
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        PersistedLoginState persistedState = findPersistedLoginState();

        assertThat(persistedState.loginFailureCount()).isEqualTo(3);
        assertThat(persistedState.lastLoginAt())
                .isEqualTo(ORIGINAL_LOGIN_AT);
        assertThat(persistedState.lastLoginIp())
                .isEqualTo(ORIGINAL_LOGIN_IP);
        assertThat(persistedState.previousLoginAt()).isNull();
        assertThat(countLoginAudits()).isZero();
    }

    private Long insertCustomer() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        jdbcTemplate.update("""
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    login_failure_count,
                    account_locked,
                    last_login_at,
                    last_login_ip,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, 3, FALSE, ?, ?,
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                )
                """,
                USER_ID,
                passwordHash,
                "원자성테스트",
                "1990-01-01",
                EMAIL,
                "01012345678",
                ORIGINAL_LOGIN_AT,
                ORIGINAL_LOGIN_IP
        );

        return jdbcTemplate.queryForObject(
                "SELECT customer_id FROM customer WHERE user_id = ?",
                Long.class,
                USER_ID
        );
    }

    private PersistedLoginState findPersistedLoginState() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    login_failure_count,
                    last_login_at,
                    last_login_ip,
                    previous_login_at
                FROM customer
                WHERE customer_id = ?
                """,
                (resultSet, rowNumber) -> new PersistedLoginState(
                        resultSet.getInt("login_failure_count"),
                        resultSet.getObject(
                                "last_login_at",
                                LocalDateTime.class
                        ),
                        resultSet.getString("last_login_ip"),
                        resultSet.getObject(
                                "previous_login_at",
                                LocalDateTime.class
                        )
                ),
                customerId
        );
    }

    private int countLoginAudits() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_log
                WHERE customer_id = ?
                  AND event_type = 'LOGIN'
                """,
                Integer.class,
                customerId
        );

        return count == null ? 0 : count;
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
                USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM customer WHERE user_id = ?",
                USER_ID
        );
    }

    // 로그인 성공 처리 전후의 고객 상태 비교 결과
    private record PersistedLoginState(
            int loginFailureCount,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            LocalDateTime previousLoginAt
    ) {
    }
}
