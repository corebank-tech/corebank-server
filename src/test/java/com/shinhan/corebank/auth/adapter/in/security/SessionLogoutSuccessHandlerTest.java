package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SessionLogoutSuccessHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionAuthenticationEntryPoint entryPoint = new SessionAuthenticationEntryPoint(objectMapper);
    private final SessionLogoutSuccessHandler logoutSuccessHandler =
            new SessionLogoutSuccessHandler(objectMapper, entryPoint);

    @Test
    @DisplayName("인증된 고객의 로그아웃 성공을 공통 JSON 응답으로 반환한다")
    void writesLogoutSuccessResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        logoutSuccessHandler.onLogoutSuccess(
                new MockHttpServletRequest(),
                response,
                UsernamePasswordAuthenticationToken.authenticated("customer", null, java.util.List.of()));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body.get("code").asText()).isEqualTo("0000");
        assertThat(body.get("message").asText()).isEqualTo("로그아웃되었습니다.");
        assertThat(body.get("data").isNull()).isTrue();
        assertCachePreventionHeaders(response);
    }

    @Test
    @DisplayName("인증 세션이 없으면 401 CMN0101을 반환한다")
    void rejectsLogoutWithoutAuthentication() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        logoutSuccessHandler.onLogoutSuccess(new MockHttpServletRequest(), response, null);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("CMN0101");
        assertThat(body.get("data").isNull()).isTrue();
        assertCachePreventionHeaders(response);
    }

    private void assertCachePreventionHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isEqualTo(0);
    }
}
