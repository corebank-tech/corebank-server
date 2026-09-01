package com.shinhan.corebank.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.application.port.out.AccountPasswordChangeAuthVerificationPort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 계좌상세 및 계좌비밀번호 검증 API의 실제 DB·Redis 계약을 검증한다.
@AutoConfigureMockMvc
@Transactional
class AccountPasswordControllerTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(110_550_052_000L);

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

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AccountPasswordChangeAuthVerificationPort changeAuthVerificationPort;

    @Test
    @DisplayName("계좌상세 조회는 비밀번호 오류 상태와 명세 기준 출금 가능 잔액을 반환한다")
    void returnsAccountDetail() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 2);

        mockMvc.perform(get("/accounts/{accountId}", account.getAccountId())
                        .with(authentication(authenticationOf(customerId))))
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
                        post("/accounts/{accountId}/password/verify", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountPassword":"1234"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.isMatched").value(true))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.remainingAttempts").value(5))
                .andExpect(jsonPath("$.data.accountPasswordAuthToken").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        String token = body.get("data").get("accountPasswordAuthToken").asText();
        AccountPasswordAuthTokenVerification verification =
                new AccountPasswordAuthTokenVerification(token, customerId, account.getAccountId());

        authTokenVerifier.verifyAndConsume(verification);
        assertThatThrownBy(() -> authTokenVerifier.verifyAndConsume(verification))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("비밀번호 오답은 APW0001과 증가한 오류 횟수를 반환하고 DB에 보존한다")
    void incrementsPasswordFailureCount() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);

        mockMvc.perform(
                        post("/accounts/{accountId}/password/verify", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountPassword":"9999"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APW0001"))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.remainingAttempts").value(4));

        Account updated = accountPersistencePort
                .findByAccountIdAndCustomerId(account.getAccountId(), customerId)
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
                        post("/accounts/{accountId}/password/verify", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountPassword":"9999"}
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APW0101"))
                .andExpect(jsonPath("$.data.errorCount").value(5))
                .andExpect(jsonPath("$.data.remainingAttempts").value(0));

        mockMvc.perform(get("/accounts/{accountId}", account.getAccountId())
                        .with(authentication(authenticationOf(customerId))))
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
                        post("/accounts/{accountId}/password/verify", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountPassword":"123"}
                                        """))
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
                                .content(
                                        """
                                        {"accountPassword":"1234"}
                                        """))
                .andExpect(status().isUnauthorized());

        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);
        mockMvc.perform(
                        post("/accounts/{accountId}/password/verify", account.getAccountId())
                                .with(authentication(authenticationOf(customerId)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountPassword":"1234"}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("두 인증을 완료한 고객은 계좌비밀번호를 변경하고 오류 상태를 초기화한다")
    void changesAccountPassword() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 2);

        mockMvc.perform(put("/accounts/{accountId}/password", account.getAccountId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequest("otp-token", "password-token", "5678", "5678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("계좌비밀번호가 변경되었습니다."))
                .andExpect(jsonPath("$.data.accountId").value(account.getAccountId()))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        Account changed = accountPersistencePort
                .findByAccountIdAndCustomerId(account.getAccountId(), customerId)
                .orElseThrow();
        assertThat(passwordEncoder.matches("5678", changed.getPasswordHash())).isTrue();
        assertThat(changed.getPasswordFailureCount()).isZero();
        assertThat(changed.isPasswordLocked()).isFalse();

        verify(changeAuthVerificationPort)
                .verifyAccountPasswordToken("password-token", customerId, account.getAccountId());
        verify(changeAuthVerificationPort).verifyOtpToken("otp-token", customerId, account.getAccountId());
    }

    @Test
    @DisplayName("같은 멱등키와 변경값은 인증 토큰이 달라도 최초 응답을 재생한다")
    void replaysResponseWithoutConsumingNewTokens() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);
        String idempotencyKey = "1e7a10b2-2c2c-4ad9-a02d-40ce9bfb8323";

        MvcResult first = performPasswordChange(
                customerId, account.getAccountId(), idempotencyKey, "first-otp-token", "first-password-token");
        // 실제 후속 HTTP 요청처럼 이전 요청의 JPA 영속성 컨텍스트를 비운다.
        entityManager.clear();
        MvcResult replay = performPasswordChange(
                customerId, account.getAccountId(), idempotencyKey, "new-otp-token", "new-password-token");

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode replayBody = objectMapper.readTree(replay.getResponse().getContentAsString());
        assertThat(replayBody.get("code").asText())
                .isEqualTo(firstBody.get("code").asText());
        assertThat(replayBody.get("message").asText())
                .isEqualTo(firstBody.get("message").asText());
        assertThat(replayBody.at("/data/accountId").asLong())
                .isEqualTo(firstBody.at("/data/accountId").asLong());
        // 공통 replay의 offset 표현 논의와 분리해 같은 instant인지 검증한다.
        assertThat(OffsetDateTime.parse(replayBody.at("/data/updatedAt").asText()))
                .isEqualTo(OffsetDateTime.parse(firstBody.at("/data/updatedAt").asText()));
        verify(changeAuthVerificationPort, times(1))
                .verifyAccountPasswordToken("first-password-token", customerId, account.getAccountId());
        verify(changeAuthVerificationPort, times(1))
                .verifyOtpToken("first-otp-token", customerId, account.getAccountId());
    }

    @Test
    @DisplayName("신규 비밀번호 확인값 불일치는 APW0002이고 인증을 소비하지 않는다")
    void rejectsNewPasswordConfirmationMismatch() throws Exception {
        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);

        mockMvc.perform(put("/accounts/{accountId}/password", account.getAccountId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", "4ab41240-c137-4eab-b816-b7b9e6647131")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequest("otp-token", "password-token", "5678", "1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APW0002"));

        verifyNoInteractions(changeAuthVerificationPort);
    }

    @Test
    @DisplayName("비로그인·CSRF 누락·멱등키 누락 계좌비밀번호 변경 요청은 차단한다")
    void blocksInvalidSecurityOrIdempotencyRequests() throws Exception {
        String request = changeRequest("otp-token", "password-token", "5678", "5678");

        mockMvc.perform(put("/accounts/{accountId}/password", 1L)
                        .with(csrf())
                        .header("Idempotency-Key", "a5829b6a-53c8-4a51-a55a-7465efb66428")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        Long customerId = customerTestFixture.createCustomer();
        Account account = saveAccount(customerId, 0);
        mockMvc.perform(put("/accounts/{accountId}/password", account.getAccountId())
                        .with(authentication(authenticationOf(customerId)))
                        .header("Idempotency-Key", "d046593a-45b8-49f9-80ad-51bcc2687cf1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/accounts/{accountId}/password", account.getAccountId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        verifyNoInteractions(changeAuthVerificationPort);
    }

    // 동일 계좌 변경 요청을 다른 토큰으로 재시도할 수 있도록 공통 요청을 수행한다.
    private MvcResult performPasswordChange(
            Long customerId, Long accountId, String idempotencyKey, String otpToken, String passwordToken)
            throws Exception {
        return mockMvc.perform(put("/accounts/{accountId}/password", accountId)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequest(otpToken, passwordToken, "5678", "5678")))
                .andExpect(status().isOk())
                .andReturn();
    }

    // 계좌비밀번호 변경 API의 JSON 요청 본문을 테스트용으로 생성한다.
    private String changeRequest(String otpToken, String passwordToken, String newPassword, String confirmation) {
        return """
                {
                  "otpAuthToken":"%s",
                  "accountPasswordAuthToken":"%s",
                  "newAccountPassword":"%s",
                  "newAccountPasswordConfirm":"%s"
                }
                """
                .formatted(otpToken, passwordToken, newPassword, confirmation);
    }

    private Account saveAccount(Long customerId, int failureCount) {
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
                null);

        return accountPersistencePort.save(account);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");

        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
