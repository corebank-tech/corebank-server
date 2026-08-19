package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementUseCase;
import com.shinhan.corebank.signup.application.port.in.GetSignupTermsUseCase;
import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SignupTermsController.class)
@TestPropertySource(properties =
        "app.security.cors.allowed-origins=http://localhost:5173")
@Import({
        SecurityConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class,
        SessionLogoutSuccessHandler.class
})
class SignupTermsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetSignupTermsUseCase getSignupTermsUseCase;

    @MockitoBean
    CheckTermsAgreementUseCase checkTermsAgreementUseCase;

    @Test
    @DisplayName("GET /api/v1/auth/terms는 회원가입 약관 목록을 반환한다")
    void getTermsReturnsSignupTerms() throws Exception {
        given(getSignupTermsUseCase.getSignupTerms())
                .willReturn(List.of(new SignupTerm(
                        "1",
                        "TERMS_SERVICE",
                        "v1.0",
                        "서비스 이용약관",
                        "서비스 이용약관 내용",
                        true,
                        false
                )));

        mockMvc.perform(get("/api/v1/auth/terms")
                        .contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].termsId").value("1"))
                .andExpect(jsonPath("$.data.items[0].termsCode")
                        .value("TERMS_SERVICE"))
                .andExpect(jsonPath("$.data.items[0].version").value("v1.0"))
                .andExpect(jsonPath("$.data.items[0].isRequired").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/terms/check는 termsAuthToken을 반환한다")
    void checkTermsReturnsTermsAuthToken() throws Exception {
        given(checkTermsAgreementUseCase.checkTermsAgreement(
                any(CheckTermsAgreementCommand.class)
        )).willReturn(new TermsAgreementResult(
                "TERMS_AUTH_test-token",
                1800L
        ));

        performCheckTerms(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.termsAuthToken")
                        .value("TERMS_AUTH_test-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    @Test
    @DisplayName("agreedTerms가 비어 있으면 CMN0001을 반환한다")
    void rejectsEmptyAgreedTerms() throws Exception {
        performCheckTerms("""
                {
                  "agreedTerms": []
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verify(checkTermsAgreementUseCase, never())
                .checkTermsAgreement(any(CheckTermsAgreementCommand.class));
    }

    @Test
    @DisplayName("termsId가 양수가 아니면 CMN0001을 반환한다")
    void rejectsInvalidTermsId() throws Exception {
        performCheckTerms("""
                {
                  "agreedTerms": [
                    {
                      "termsId": "",
                      "version": "v1.0",
                      "isAgreed": true,
                      "isRead": true
                    }
                  ]
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verify(checkTermsAgreementUseCase, never())
                .checkTermsAgreement(any(CheckTermsAgreementCommand.class));
    }

    @Test
    @DisplayName("필수 약관 미동의는 ATH0006을 반환한다")
    void returnsRequiredTermsNotAgreed() throws Exception {
        given(checkTermsAgreementUseCase.checkTermsAgreement(
                any(CheckTermsAgreementCommand.class)
        )).willThrow(new BusinessException(
                SignupErrorCode.REQUIRED_TERMS_NOT_AGREED
        ));

        performCheckTerms(validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATH0006"))
                .andExpect(jsonPath("$.message")
                        .value("필수 약관에 동의하지 않았습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions performCheckTerms(
            String content
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/terms/check")
                .contextPath("/api/v1")
                .contentType(APPLICATION_JSON)
                .content(content));
    }

    private String validRequest() {
        return """
                {
                  "agreedTerms": [
                    {
                      "termsId": "1",
                      "version": "v1.0",
                      "isAgreed": true,
                      "isRead": true
                    }
                  ]
                }
                """;
    }
}
