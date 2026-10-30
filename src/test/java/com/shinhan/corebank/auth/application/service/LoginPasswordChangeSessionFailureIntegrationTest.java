package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordCommand;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class LoginPasswordChangeSessionFailureIntegrationTest extends IntegrationTestSupport {
    private static final String USER_ID = "session-failure-638";
    private static final String ORIGINAL_PASSWORD = "CorrectPassword1!";
    private static final String NEW_PASSWORD = "SaferPass2!";

    @Autowired
    private ChangeLoginPasswordUseCase changePassword;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 세션 저장소 장애를 주입해 비밀번호와 감사 로그가 롤백되는지 본다 — 실제 세션 테이블로는 삭제 실패를 재현할 수 없다.
    @MockitoBean
    private CustomerSessionInvalidator sessionInvalidator;

    private Long customerId;
    private String originalHash;

    @BeforeEach
    void setUp() {
        deleteCustomer();
        originalHash = passwordEncoder.encode(ORIGINAL_PASSWORD);
        jdbc.update(
                """
                INSERT INTO customer (
                    user_id, password_hash, user_name, birth_date, email, phone_number,
                    login_failure_count, account_locked, joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, FALSE, NOW(6), NOW(6), NOW(6))
                """,
                USER_ID,
                originalHash,
                "세션실패테스트",
                "1990-01-01",
                "session-failure-638@example.com",
                "01012345678");
        customerId = jdbc.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, USER_ID);
    }

    @AfterEach
    void tearDown() {
        deleteCustomer();
    }

    @Test
    void sessionDeletionFailureRollsBackPasswordAndAudit() {
        doThrow(new DataAccessResourceFailureException("session store unavailable"))
                .when(sessionInvalidator)
                .invalidateAll(customerId);

        // 실제 비밀번호 변경 서비스의 커밋 단계에서 세션 삭제 실패를 주입한다.
        assertThatThrownBy(() -> changePassword.change(new ChangeLoginPasswordCommand(
                        customerId, USER_ID, ORIGINAL_PASSWORD, NEW_PASSWORD, NEW_PASSWORD, "127.0.0.1")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        String persistedHash = jdbc.queryForObject(
                "SELECT password_hash FROM customer WHERE customer_id = ?", String.class, customerId);
        assertThat(persistedHash).isEqualTo(originalHash);
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE customer_id = ? AND event_type = 'LOGIN_PASSWORD_CHANGE'",
                Integer.class,
                customerId);
        assertThat(auditCount).isZero();
    }

    private void deleteCustomer() {
        jdbc.update(
                "DELETE FROM audit_log WHERE customer_id IN (SELECT customer_id FROM customer WHERE user_id = ?)",
                USER_ID);
        jdbc.update("DELETE FROM customer WHERE user_id = ?", USER_ID);
    }
}
