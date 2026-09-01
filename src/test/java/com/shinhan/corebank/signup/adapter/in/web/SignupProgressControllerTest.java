package com.shinhan.corebank.signup.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.GetSignupConfirmationUseCase;
import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupResult;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 회원가입 입력 검증과 확인정보 조회 API의 공개 접근 및 응답 계약을 검증한다.
@WebMvcTest(controllers = SignupProgressController.class)
@TestPropertySource(properties = "app.security.cors.allowed-origins=http://localhost:5173")
@Import({
    SecurityConfig.class,
    SessionAuthenticationEntryPoint.class,
    SessionAccessDeniedHandler.class,
    SessionLogoutSuccessHandler.class
})
class SignupProgressControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ValidateSignupUseCase validateSignupUseCase;

    @MockitoBean
    GetSignupConfirmationUseCase confirmationUseCase;

    @Test
    void validatesWithoutAuthenticationOrCsrf() throws Exception {
        given(validateSignupUseCase.validate(any())).willReturn(new ValidateSignupResult("TEMP_SIGNUP_token", 1800L));

        mockMvc.perform(post("/api/v1/auth/signup/validate")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.tempSignupToken").value("TEMP_SIGNUP_token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    @Test
    void invalidRequestReturnsCanonicalCmn0001() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/validate")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void invalidUserIdFormatReturnsCmn0001BeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/validate")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest().replace("honggildong", "Invalid-ID")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    void invalidEmailFormatReturnsCmn0001BeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/validate")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest().replace("hong@corebank.example.com", "invalid-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    void returnsMaskedConfirmationWithoutAuthentication() throws Exception {
        given(confirmationUseCase.getConfirmation("TEMP_SIGNUP_token"))
                .willReturn(new SignupConfirmationResult(
                        "홍*동", "honggildong", "90.01.01", "010-****-5678", "hon*@corebank.example.com"));

        mockMvc.perform(get("/api/v1/auth/signup/confirm-info")
                        .contextPath("/api/v1")
                        .header("X-Signup-Token", "TEMP_SIGNUP_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("홍*동"))
                .andExpect(jsonPath("$.data.birthDate").value("90.01.01"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.data.email").value("hon*@corebank.example.com"));
    }

    @Test
    void missingSignupHeaderReturnsCmn0001() throws Exception {
        given(confirmationUseCase.getConfirmation(isNull()))
                .willThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/api/v1/auth/signup/confirm-info").contextPath("/api/v1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    void corsAllowsSignupTokenHeader() throws Exception {
        mockMvc.perform(options("/api/v1/auth/signup/confirm-info")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Signup-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", "X-Signup-Token"));
    }

    private String validRequest() {
        return """
                {
                  "termsAuthToken":"TERMS",
                  "accountAuthToken":"ACCOUNT",
                  "userIdCheckToken":"USER_ID",
                  "emailVerificationToken":"EMAIL",
                  "tempSignupToken":null,
                  "userId":"honggildong",
                  "userPassword":"Password123!",
                  "userPasswordConfirm":"Password123!",
                  "email":"hong@corebank.example.com",
                  "phoneNumber":"01012345678"
                }
                """;
    }
}
