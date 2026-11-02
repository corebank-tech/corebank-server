package com.shinhan.corebank.subscription.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.subscription.early-termination.enabled=true")
@DisplayName("PH-16 중도해지 Controller API 테스트")
class EarlyTerminationControllerTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long ACCOUNT_ID = 101L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 202L;

    private static final String ENDPOINT = "POST /accounts/101/early-termination";
    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String AUTH_TOKEN = "test-account-password-token";

    @Autowired
    private MockMvc mockMvc;

    // HTTP 경계만 검증하고 실제 금융 거래는 실행하지 않는다.
    @MockitoBean
    private EarlyTerminationUseCase terminationUseCase;

    // 실제 IdempotentRequestExecutor는 유지한다.
    @MockitoBean
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(idempotencyService.begin(anyString(), eq(CUSTOMER_ID), anyString(), anyString()))
                .thenReturn(IdempotencyResult.proceed());
    }

    @Test
    @DisplayName("정상 중도해지는 200과 원리금 정산 결과를 반환한다")
    void terminate_success() throws Exception {
        when(terminationUseCase.terminate(new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN)))
                .thenReturn(result());

        performValidRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("중도해지가 완료되었습니다."))
                .andExpect(jsonPath("$.data.accountId").value(101))
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(202))
                .andExpect(jsonPath("$.data.principal").value(1_000_000))
                .andExpect(jsonPath("$.data.grossInterest").value(10_000))
                .andExpect(jsonPath("$.data.tax").value(1_540))
                .andExpect(jsonPath("$.data.netInterest").value(8_460))
                .andExpect(jsonPath("$.data.totalTransferred").value(1_008_460))
                .andExpect(jsonPath("$.data.transactionNumber").value("20261102WB0000000001"))
                .andExpect(jsonPath("$.data.tradeDate").value("2026-11-02"))
                .andExpect(jsonPath("$.data.closedAt").value("2026-11-02T13:00:00"));

        verify(terminationUseCase).terminate(new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN));

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);

        verify(idempotencyService).begin(eq(IDEMPOTENCY_KEY), eq(CUSTOMER_ID), eq(ENDPOINT), fingerprint.capture());

        assertThat(fingerprint.getValue()).contains("\"accountId\":101");

        verify(idempotencyService).complete(eq(IDEMPOTENCY_KEY), eq((short) 200), anyString());
    }

    @Test
    @DisplayName("동일 멱등키 재시도는 최초 응답을 재생하고 중복 실행하지 않는다")
    void terminate_replay() throws Exception {
        when(terminationUseCase.terminate(new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN)))
                .thenReturn(result());

        String firstResponse = performValidRequest()
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        when(idempotencyService.begin(eq(IDEMPOTENCY_KEY), eq(CUSTOMER_ID), eq(ENDPOINT), anyString()))
                .thenReturn(IdempotencyResult.replay((short) 200, firstResponse));

        performValidRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.totalTransferred").value(1_008_460));

        verify(terminationUseCase, times(1))
                .terminate(new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN));

        verify(idempotencyService, times(1)).complete(eq(IDEMPOTENCY_KEY), eq((short) 200), anyString());
    }

    @Test
    @DisplayName("타인 소유 계좌는 404로 거부하고 멱등키를 해제한다")
    void terminate_otherCustomersAccount() throws Exception {
        when(terminationUseCase.terminate(new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN)))
                .thenThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        performValidRequest()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACC0201"));

        verify(idempotencyService).release(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("다른 요청에 사용된 멱등키는 409로 거부한다")
    void terminate_idempotencyConflict() throws Exception {
        when(idempotencyService.begin(eq(IDEMPOTENCY_KEY), eq(CUSTOMER_ID), eq(ENDPOINT), anyString()))
                .thenThrow(new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST));

        performValidRequest()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));

        verifyNoInteractions(terminationUseCase);
    }

    @Test
    @DisplayName("멱등키 헤더가 없으면 400을 반환한다")
    void terminate_missingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/early-termination", ACCOUNT_ID)
                        .with(authentication(authenticationOf(CUSTOMER_ID)))
                        .with(csrf())
                        .header("Account-Password-Auth-Token", AUTH_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        verifyNoInteractions(terminationUseCase);
    }

    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 없으면 400을 반환한다")
    void terminate_missingAuthToken() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/early-termination", ACCOUNT_ID)
                        .with(authentication(authenticationOf(CUSTOMER_ID)))
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        verifyNoInteractions(terminationUseCase);
    }

    @Test
    @DisplayName("계좌 ID가 0이면 400을 반환한다")
    void terminate_invalidAccountId() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/early-termination", 0L)
                        .with(authentication(authenticationOf(CUSTOMER_ID)))
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Account-Password-Auth-Token", AUTH_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verifyNoInteractions(terminationUseCase);
    }

    @Test
    @DisplayName("비로그인 요청은 401로 거부한다")
    void terminate_unauthenticated() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/early-termination", ACCOUNT_ID)
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Account-Password-Auth-Token", AUTH_TOKEN))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(terminationUseCase);
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 403으로 거부한다")
    void terminate_missingCsrf() throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/early-termination", ACCOUNT_ID)
                        .with(authentication(authenticationOf(CUSTOMER_ID)))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Account-Password-Auth-Token", AUTH_TOKEN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(terminationUseCase);
    }

    private ResultActions performValidRequest() throws Exception {
        return mockMvc.perform(post("/accounts/{accountId}/early-termination", ACCOUNT_ID)
                .with(authentication(authenticationOf(CUSTOMER_ID)))
                .with(csrf())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .header("Account-Password-Auth-Token", AUTH_TOKEN));
    }

    private static EarlyTerminationResult result() {
        return new EarlyTerminationResult(
                ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID,
                1_000_000L,
                10_000L,
                1_540L,
                8_460L,
                1_008_460L,
                "20261102WB0000000001",
                LocalDate.of(2026, 11, 2),
                LocalDateTime.of(2026, 11, 2, 13, 0));
    }

    private static UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");

        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
