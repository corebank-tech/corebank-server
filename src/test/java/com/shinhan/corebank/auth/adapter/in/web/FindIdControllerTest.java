package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.adapter.in.security.SecurityConfig;
import com.shinhan.corebank.auth.adapter.in.security.SessionAccessDeniedHandler;
import com.shinhan.corebank.auth.adapter.in.security.SessionAuthenticationEntryPoint;
import com.shinhan.corebank.auth.adapter.in.security.SessionLogoutSuccessHandler;
import com.shinhan.corebank.auth.application.port.in.FindIdCommand;
import com.shinhan.corebank.auth.application.port.in.FindIdResult;
import com.shinhan.corebank.auth.application.port.in.FindIdUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 아이디 찾기 API의 공개 접근, 요청 변환, 성공·누락 응답 계약을 검증한다.
@WebMvcTest(controllers = FindIdController.class)
@Import({
        SecurityConfig.class,
        SessionAuthenticationEntryPoint.class,
        SessionAccessDeniedHandler.class,
        SessionLogoutSuccessHandler.class
})
class FindIdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindIdUseCase findIdUseCase;

    @Test
    @DisplayName("세션과 CSRF 토큰 없이 본인확인 후 전체 아이디를 반환한다")
    void returnsFullUserId() throws Exception {
        FindIdCommand command = new FindIdCommand(
                "홍길동",
                "1999-01-01",
                "110550051877",
                "1234"
        );
        given(findIdUseCase.findId(command))
                .willReturn(new FindIdResult("DUDGNS389"));

        mockMvc.perform(post("/api/v1/auth/find-id")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "홍길동",
                                  "birthDate": "1999-01-01",
                                  "accountNumber": "110550051877",
                                  "accountPassword": "1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("아이디를 조회했습니다."))
                .andExpect(jsonPath("$.data.userId").value("DUDGNS389"));

        verify(findIdUseCase).findId(command);
    }

    @Test
    @DisplayName("요청 본문이 없으면 CMN0002를 반환한다")
    void rejectsMissingRequestBody() throws Exception {
        given(findIdUseCase.findId(null)).willThrow(
                new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING)
        );

        mockMvc.perform(post("/api/v1/auth/find-id")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("요청 문자열에는 계좌비밀번호를 노출하지 않는다")
    void protectsAccountPasswordInToString() {
        FindIdRequest request = new FindIdRequest(
                "홍길동",
                "1999-01-01",
                "110550051877",
                "1234"
        );

        assertThat(request.toString())
                .contains("customerName=[PROTECTED]")
                .contains("birthDate=[PROTECTED]")
                .contains("accountNumber=[PROTECTED]")
                .contains("accountPassword=[PROTECTED]")
                .doesNotContain(
                        "홍길동",
                        "1999-01-01",
                        "110550051877",
                        "1234"
                );
    }
}
