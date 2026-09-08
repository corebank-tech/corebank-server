package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MySQL에서 고객·계좌 조회와 계좌비밀번호 실패 잠금까지 아이디 찾기 전체 흐름을 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
class FindIdApiIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "find-id-user";
    private static final String ACCOUNT_NUMBER = "110550051877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        deleteTestData();
        Long customerId = insertCustomer();
        insertAccount(customerId);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("올바른 고객·계좌정보로 전체 로그인 아이디를 찾는다")
    void findsFullUserId() throws Exception {
        mockMvc.perform(findIdRequest("1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.userId").value(USER_ID));
    }

    @Test
    @DisplayName("계좌비밀번호가 5회 틀리면 계좌를 잠그고 ATH0102를 반환한다")
    void locksAccountAfterFivePasswordFailures() throws Exception {
        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(findIdRequest("9999"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ATH0009"));
        }

        mockMvc.perform(findIdRequest("9999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATH0102"));

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT password_failure_count FROM account WHERE account_number = ?",
                Integer.class,
                ACCOUNT_NUMBER
        );
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT password_locked FROM account WHERE account_number = ?",
                Boolean.class,
                ACCOUNT_NUMBER
        );
        assertThat(failureCount).isEqualTo(5);
        assertThat(locked).isTrue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    findIdRequest(String accountPassword) {
        return post("/api/v1/auth/find-id")
                .contextPath("/api/v1")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "아이디찾기고객",
                          "birthDate": "1999-01-01",
                          "accountNumber": "%s",
                          "accountPassword": "%s"
                        }
                        """.formatted(ACCOUNT_NUMBER, accountPassword));
    }

    private Long insertCustomer() {
        jdbcTemplate.update("""
                INSERT INTO customer (
                    user_id, password_hash, user_name, birth_date,
                    email, phone_number, joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))
                """,
                USER_ID,
                passwordEncoder.encode("LoginPassword1!"),
                "아이디찾기고객",
                "1999-01-01",
                "find-id-user@example.com",
                "01098765432"
        );
        return jdbcTemplate.queryForObject(
                "SELECT customer_id FROM customer WHERE user_id = ?",
                Long.class,
                USER_ID
        );
    }

    private void insertAccount(Long customerId) {
        jdbcTemplate.update("""
                INSERT INTO account (
                    account_number, customer_id, account_type, balance,
                    status, password_hash, opened_date, created_at, updated_at
                ) VALUES (?, ?, 'DEMAND_DEPOSIT', 100000,
                          'ACTIVE', ?, '2026-01-01', NOW(6), NOW(6))
                """,
                ACCOUNT_NUMBER,
                customerId,
                passwordEncoder.encode("1234")
        );
    }

    private void deleteTestData() {
        jdbcTemplate.update(
                "DELETE FROM account WHERE account_number = ?",
                ACCOUNT_NUMBER
        );
        jdbcTemplate.update(
                "DELETE FROM customer WHERE user_id = ?",
                USER_ID
        );
    }
}
