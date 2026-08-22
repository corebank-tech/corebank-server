package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 계좌상세 및 계좌비밀번호 검증 API의 실제 DB·Redis 계약을 검증한다.
@AutoConfigureMockMvc
@Transactional
class AccountPasswordControllerTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_SEQUENCE =
            new AtomicLong(110_550_052_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private AccountPasswordAuthTokenVerifier authTokenVerifier;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("계좌상세 조회는 비밀번호 오류 상태와 명세 기준 출금 가능 잔액을 반환한다")
    void returnsAccountDetail() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 2);

        mockMvc.perform(
                        get("/accounts/{accountId}", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.accountId").value(account.getAccountId()))
                .andExpect(jsonPath("$.data.accountName").value("입출금통장"))
                .andExpect(jsonPath("$.data.ownerName").value("테*터"))
                .andExpect(jsonPath("$.data.balance").value(1_500_000L))
                .andExpect(jsonPath("$.data.availableBalance").value(1_500_000L))
                .andExpect(jsonPath("$.data.passwordFailureCount").value(2))
                .andExpect(jsonPath("$.data.passwordLocked").value(false));
    }

    @Test
    @DisplayName("올바른 계좌비밀번호는 300초 일회용 인증 토큰을 발급한다")
    void verifiesPasswordAndIssuesConsumableToken() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);

        MvcResult mvcResult = mockMvc.perform(
                        post(
                                "/accounts/{accountId}/password/verify",
                                account.getAccountId()
                        )
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"1234"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.isMatched").value(true))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.remainingAttempts").value(5))
                .andExpect(jsonPath("$.data.accountPasswordAuthToken").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                mvcResult.getResponse().getContentAsString()
        );
        String token = body.get("data")
                .get("accountPasswordAuthToken")
                .asText();
        AccountPasswordAuthTokenVerification verification =
                new AccountPasswordAuthTokenVerification(
                        token,
                        customerId,
                        account.getAccountId()
                );

        authTokenVerifier.verifyAndConsume(verification);
        assertThatThrownBy(
                () -> authTokenVerifier.verifyAndConsume(verification)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("비밀번호 오답은 APW0001과 증가한 오류 횟수를 반환하고 DB에 보존한다")
    void incrementsPasswordFailureCount() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/password/verify",
                                account.getAccountId()
                        )
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"9999"}
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APW0001"))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.remainingAttempts").value(4));

        Account updated = accountPersistencePort
                .findByAccountIdAndCustomerId(
                        account.getAccountId(),
                        customerId
                )
                .orElseThrow();
        assertThat(updated.getPasswordFailureCount()).isEqualTo(1);
        assertThat(updated.isPasswordLocked()).isFalse();
    }

    @Test
    @DisplayName("다섯 번째 오답은 APW0101로 잠그고 상세 조회의 출금 가능 잔액을 0으로 만든다")
    void locksOnFifthFailure() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 4);

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/password/verify",
                                account.getAccountId()
                        )
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"9999"}
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APW0101"))
                .andExpect(jsonPath("$.data.errorCount").value(5))
                .andExpect(jsonPath("$.data.remainingAttempts").value(0));

        mockMvc.perform(
                        get("/accounts/{accountId}", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(0))
                .andExpect(jsonPath("$.data.passwordLocked").value(true));
    }

    @Test
    @DisplayName("숫자 4자리가 아닌 계좌비밀번호는 CMN0001을 반환한다")
    void rejectsInvalidPasswordFormat() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);

        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/password/verify",
                                account.getAccountId()
                        )
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"123"}
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("비로그인 또는 CSRF 없는 계좌비밀번호 검증 요청은 차단한다")
    void blocksUnauthenticatedOrCsrfMissingRequest() throws Exception {
        mockMvc.perform(
                        post("/accounts/{accountId}/password/verify", 1L)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"1234"}
                                        """)
                )
                .andExpect(status().isUnauthorized());

        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);
        mockMvc.perform(
                        post(
                                "/accounts/{accountId}/password/verify",
                                account.getAccountId()
                        )
                                .with(authentication(authenticationOf(customerId)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"accountPassword":"1234"}
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    private Account saveAccount(
            Long customerId,
            int failureCount
    ) {
        Account account = Account.reconstitute(
                null,
                String.format("%012d", ACCOUNT_SEQUENCE.incrementAndGet()),
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_500_000L,
                com.shinhan.corebank.account.domain.AccountStatus.ACTIVE,
                passwordEncoder.encode("1234"),
                failureCount,
                false,
                null,
                null,
                true,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2025, 3, 10, 0, 0),
                null,
                null,
                null,
                null,
                null,
                null
        );

        return accountPersistencePort.save(account);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(
            Long customerId
    ) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(
                customerId,
                "user" + customerId,
                "테스터"
        );

        return UsernamePasswordAuthenticationToken.authenticated(
                customer,
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );
    }
}
