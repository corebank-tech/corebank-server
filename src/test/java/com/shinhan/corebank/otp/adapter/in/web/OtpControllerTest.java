package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.config.OtpProperties;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import com.shinhan.corebank.otp.domain.model.OtpAttemptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OTP 발급·검증 JSON 계약과 세션·CSRF 보호 및 오류 횟수 응답을 검증한다.
@AutoConfigureMockMvc
class OtpControllerTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;

    @MockitoBean IssueOtpUseCase issueOtpUseCase;
    @MockitoBean VerifyOtpUseCase verifyOtpUseCase;
    @MockitoBean OtpProperties otpProperties;

    @Test
    @DisplayName("OTP 발급 성공 응답에 요청 ID와 6자리 번호 및 180초를 반환한다")
    void issuesOtpSuccessfully() throws Exception {
        given(otpProperties.exposeCode()).willReturn(true);
        given(issueOtpUseCase.issue(any())).willReturn(
                new IssueOtpResult("OTP_REQ_test", "012345", 180)
        );

        mockMvc.perform(post("/otp/issue")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(issueBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("OTP가 성공적으로 발급되었습니다."))
                .andExpect(jsonPath("$.data.otpRequestId").value("OTP_REQ_test"))
                .andExpect(jsonPath("$.data.otpCode").value("012345"))
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    @DisplayName("OTP 번호 노출이 꺼지면 발급 성공 응답에서 평문 번호를 제외한다")
    void omitsOtpCodeWhenExposureIsDisabled() throws Exception {
        given(otpProperties.exposeCode()).willReturn(false);
        given(issueOtpUseCase.issue(any())).willReturn(
                new IssueOtpResult("OTP_REQ_test", "012345", 180)
        );

        mockMvc.perform(post("/otp/issue")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(issueBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequestId").value("OTP_REQ_test"))
                .andExpect(jsonPath("$.data.otpCode").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    @DisplayName("OTP 검증 성공 응답에 인증 토큰과 null 오류 횟수를 반환한다")
    void verifiesOtpSuccessfully() throws Exception {
        given(verifyOtpUseCase.verify(any())).willReturn(
                new VerifyOtpResult("OTP_AUTH_test")
        );

        performVerify("123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("OTP 검증이 완료되었습니다."))
                .andExpect(jsonPath("$.data.otpAuthToken").value("OTP_AUTH_test"))
                .andExpect(jsonPath("$.data.errorCount").value((Object) null))
                .andExpect(jsonPath("$.data.remainingAttempts").value((Object) null));
    }

    @Test
    @DisplayName("6자리 미만 OTP는 CMN0001을 반환하고 유스케이스를 호출하지 않는다")
    void rejectsShortOtpCode() throws Exception {
        performVerify("12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));

        verify(verifyOtpUseCase, never()).verify(any());
    }

    @Test
    @DisplayName("OTP 오답은 OTP0001과 현재 오류 횟수 및 잔여 횟수를 반환한다")
    void returnsMismatchAttemptData() throws Exception {
        given(verifyOtpUseCase.verify(any())).willThrow(
                new OtpVerificationFailedException(new OtpAttemptResult(1, 4, false))
        );

        performVerify("000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP0001"))
                .andExpect(jsonPath("$.data.otpAuthToken").value((Object) null))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.remainingAttempts").value(4));
    }

    @Test
    @DisplayName("다섯 번째 OTP 오답은 OTP0103과 5/0을 반환한다")
    void returnsLockedAttemptData() throws Exception {
        given(verifyOtpUseCase.verify(any())).willThrow(
                new OtpVerificationFailedException(new OtpAttemptResult(5, 0, true))
        );

        performVerify("000000")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTP0103"))
                .andExpect(jsonPath("$.data.errorCount").value(5))
                .andExpect(jsonPath("$.data.remainingAttempts").value(0));
    }

    @Test
    @DisplayName("로그인하지 않은 OTP 발급 요청은 CMN0101을 반환한다")
    void rejectsIssueWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/otp/issue")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(issueBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));

        verify(issueOtpUseCase, never()).issue(any());
    }

    @Test
    @DisplayName("CSRF 토큰이 없는 로그인 고객의 OTP 발급 요청은 CMN0102를 반환한다")
    void rejectsIssueWithoutCsrf() throws Exception {
        mockMvc.perform(post("/otp/issue")
                        .with(authentication(authenticationOf(1L)))
                        .contentType(APPLICATION_JSON)
                        .content(issueBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));

        verify(issueOtpUseCase, never()).issue(any());
    }

    private org.springframework.test.web.servlet.ResultActions performVerify(String otpCode)
            throws Exception {
        return mockMvc.perform(post("/otp/verify")
                .with(authentication(authenticationOf(1L)))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "otpRequestId": "OTP_REQ_test",
                          "otpCode": "%s"
                        }
                        """.formatted(otpCode)));
    }

    private String issueBody() {
        return """
                {
                  "transactionType": "IMMEDIATE_TRANSFER",
                  "transactionData": {
                    "withdrawalAccountId": 101,
                    "depositAccountNumber": "110660000103",
                    "amount": 100000
                  }
                }
                """;
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
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
