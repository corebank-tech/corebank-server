package com.shinhan.corebank.subscription.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.subscription.api.TerminationPreviewResult;
import com.shinhan.corebank.subscription.api.TerminationPreviewUseCase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.subscription.early-termination.enabled=true")
@DisplayName("중도해지 예상 조회 Controller 테스트")
class EarlyTerminationPreviewControllerTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long ACCOUNT_ID = 101L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 202L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TerminationPreviewUseCase previewUseCase;

    @Test
    @DisplayName("정상 조회는 중도해지 예상 원리금을 반환한다")
    void preview_success() throws Exception {
        when(previewUseCase.previewEarly(CUSTOMER_ID, ACCOUNT_ID)).thenReturn(result());

        mockMvc.perform(get("/accounts/{accountId}/early-termination/preview", ACCOUNT_ID)
                        .with(authentication(authenticationOf(CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.accountId").value(101))
                .andExpect(jsonPath("$.data.terminationType").value("EARLY_TERMINATION"))
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(202))
                .andExpect(jsonPath("$.data.principal").value(100_000))
                .andExpect(jsonPath("$.data.grossInterest").value(0))
                .andExpect(jsonPath("$.data.tax").value(0))
                .andExpect(jsonPath("$.data.netInterest").value(0))
                .andExpect(jsonPath("$.data.totalExpectedAmount").value(100_000))
                .andExpect(jsonPath("$.data.calculationDate").value("2026-11-12"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(previewUseCase).previewEarly(CUSTOMER_ID, ACCOUNT_ID);
    }

    @Test
    @DisplayName("타인 계좌는 404로 거부한다")
    void preview_otherCustomer() throws Exception {
        when(previewUseCase.previewEarly(CUSTOMER_ID, ACCOUNT_ID))
                .thenThrow(new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        mockMvc.perform(get("/accounts/{accountId}/early-termination/preview", ACCOUNT_ID)
                        .with(authentication(authenticationOf(CUSTOMER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACC0201"));
    }

    @Test
    @DisplayName("비로그인 요청은 401로 거부한다")
    void preview_unauthenticated() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}/early-termination/preview", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(previewUseCase);
    }

    @Test
    @DisplayName("잘못된 계좌 ID는 400으로 거부한다")
    void preview_invalidAccountId() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}/early-termination/preview", 0L)
                        .with(authentication(authenticationOf(CUSTOMER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verifyNoInteractions(previewUseCase);
    }

    private static TerminationPreviewResult result() {
        return new TerminationPreviewResult(
                ACCOUNT_ID,
                "EARLY_TERMINATION",
                WITHDRAWAL_ACCOUNT_ID,
                100_000L,
                0L,
                0L,
                0L,
                100_000L,
                LocalDate.of(2026, 11, 12),
                OffsetDateTime.parse("2026-11-12T14:30:00+09:00"));
    }

    private static UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {

        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");

        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
