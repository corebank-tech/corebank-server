package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SwaggerUiAccessIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Swagger UI 기본 진입 경로(/swagger-ui.html)는 세션 없이 접근할 수 있다")
    void permitsSwaggerUiDefaultEntryWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/swagger-ui.html").contextPath("/api/v1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/api/v1/swagger-ui/index.html"));
    }

    @Test
    @DisplayName("Swagger UI 화면(/swagger-ui/index.html)은 세션 없이 접근할 수 있다")
    void permitsSwaggerUiIndexWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/swagger-ui/index.html").contextPath("/api/v1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API 문서(/v3/api-docs)는 세션 없이 접근할 수 있다")
    void permitsApiDocsWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk());
    }

    // "/v3/api-docs/**"는 확장자가 붙은 형제 경로를 잡지 못해 이 경로만 401이던 이력이 있다.
    @Test
    @DisplayName("YAML 형식 API 문서(/v3/api-docs.yaml)도 세션 없이 접근할 수 있다")
    void permitsApiDocsYamlWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs.yaml").contextPath("/api/v1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증·고객·OTP·회원가입·약관 API가 요약과 태그를 포함해 문서화된다")
    void documentsAuthenticationAndSignupApis() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(summary("/auth/login", "post", "로그인"))
                .andExpect(summary("/customers/me", "get", "내 고객정보 조회"))
                .andExpect(summary("/customers/me", "patch", "내 고객정보 변경"))
                .andExpect(summary("/dashboard/login-status", "get", "로그인 상태 조회"))
                .andExpect(summary("/otp/issue", "post", "거래 OTP 발급"))
                .andExpect(summary("/otp/verify", "post", "거래 OTP 검증"))
                .andExpect(summary("/auth/terms", "get", "회원가입 약관 조회"))
                .andExpect(summary("/auth/terms/check", "post", "회원가입 약관 동의 검증"))
                .andExpect(summary("/auth/verify-account", "post", "회원가입 실명·계좌 인증"))
                .andExpect(summary("/auth/check-id", "post", "회원가입 아이디 중복확인"))
                .andExpect(summary("/auth/email-verifications", "post", "이메일 인증번호 발급"))
                .andExpect(summary(
                        "/auth/email-verifications/{emailVerificationId}/verify",
                        "post",
                        "이메일 인증번호 검증"
                ))
                .andExpect(summary("/auth/signup/validate", "post", "회원가입 입력정보 검증"))
                .andExpect(summary("/auth/signup/confirm-info", "get", "회원가입 확인정보 조회"))
                .andExpect(summary("/auth/signup/complete", "post", "회원가입 완료"));
    }

    @Test
    @DisplayName("민감 인증정보와 멱등키 요구사항이 OpenAPI 스키마에 반영된다")
    void documentsSensitiveInputsAndIdempotencyHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$['components']['schemas']['LoginRequest']['properties']['password']['writeOnly']"
                ).value(true))
                .andExpect(jsonPath(
                        "$['components']['schemas']['VerifyOtpRequest']['properties']['otpCode']['writeOnly']"
                ).value(true))
                .andExpect(jsonPath(
                        "$['paths']['/customers/me']['patch']['parameters'][0]['name']"
                ).value("Idempotency-Key"))
                .andExpect(jsonPath(
                        "$['paths']['/customers/me']['patch']['parameters'][0]['required']"
                ).value(true))
                .andExpect(jsonPath(
                        "$['paths']['/auth/signup/complete']['post']['parameters'][0]['name']"
                ).value("Idempotency-Key"));
    }

    @Test
    @DisplayName("보호 API는 여전히 세션 없이 접근할 수 없다")
    void stillRejectsProtectedApiWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.ResultMatcher summary(
            String path,
            String method,
            String expectedSummary
    ) {
        return jsonPath(
                "$['paths']['" + path + "']['" + method + "']['summary']"
        ).value(expectedSummary);
    }
}
