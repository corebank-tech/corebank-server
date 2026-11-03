package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldPolicy;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보류 저장이 실패한 뒤 같은 멱등 키로 다시 보내면 어떻게 끝나는지 고정한다(transfer_seam.md §9-3).
 *
 * 계좌비밀번호 토큰은 실제 저장소로 발급·소비한다. 재요청은 이 토큰에서 먼저 걸리므로 OTP 검증기만 대역으로 둔다.
 * 항상 보류하는 훅 C와 저장이 실패하는 보류 포트는 이 클래스 전용 구성이라 다른 테스트의 컨텍스트와 섞이지 않는다.
 */
@AutoConfigureMockMvc
@Import(TransferHoldStoreFailureIdempotencyTest.AlwaysHoldConfig.class)
@DisplayName("보류 저장 실패 후 같은 멱등 키 재요청")
class TransferHoldStoreFailureIdempotencyTest extends IntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestConfiguration
    static class AlwaysHoldConfig {
        @Bean
        TransferHoldPolicy alwaysHold() {
            return new TransferHoldPolicy() {
                @Override
                public int order() {
                    return 1;
                }

                @Override
                public TransferHoldDecision decide(TransferHoldContext context) {
                    return TransferHoldDecision.holdForApproval("TEST");
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountPasswordAuthTokenStorePort accountPasswordAuthTokenStorePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private TransferHoldPort transferHoldPort;

    @BeforeEach
    void seed() {
        cleanUp();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        given(transferHoldPort.save(any())).willThrow(new IllegalStateException("hold store down"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    @Test
    @DisplayName("첫 요청은 500과 ERROR 1행으로 끝나고, 같은 키 재요청은 소비된 토큰 때문에 APW0102로 끝나며 이체·원장이 늘지 않는다")
    void holdStoreFailure_thenSameKey_endsWithTokenError() throws Exception {
        String passwordToken = UUID.randomUUID().toString();
        accountPasswordAuthTokenStorePort.save(
                passwordToken, new AccountPasswordAuthTokenPayload(1L, 101L), Duration.ofMinutes(5));
        String idempotencyKey = UUID.randomUUID().toString();

        postTransfer(idempotencyKey, passwordToken).andExpect(status().isInternalServerError());

        assertThat(transferStatuses()).containsExactly("ERROR");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM transfer WHERE withdrawal_account_id = 101", String.class))
                .isEqualTo("CMN9999");

        postTransfer(idempotencyKey, passwordToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ERROR"))
                .andExpect(jsonPath("$.data.errorCode").value("APW0102"));

        assertThat(transferStatuses()).containsExactly("ERROR");
        assertThat(balanceOf(101)).isEqualTo(100_000L);
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
        assertThat(ledgerRows).isZero();
    }

    private ResultActions postTransfer(String idempotencyKey, String passwordToken) throws Exception {
        return mockMvc.perform(post("/transfers")
                .with(authentication(authenticationOf(1L)))
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .header("Account-Password-Auth-Token", passwordToken)
                .header("Otp-Auth-Token", "dummy-otp-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                        new TransferRequest(101L, "110222222222", 30_000L, null, null, null))));
    }

    private java.util.List<String> transferStatuses() {
        return jdbcTemplate.queryForList("SELECT status FROM transfer WHERE withdrawal_account_id = 101", String.class);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
