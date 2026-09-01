package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SessionAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionAuthenticationEntryPoint entryPoint = new SessionAuthenticationEntryPoint(objectMapper);

    @Test
    @DisplayName("미인증 요청을 UTF-8 JSON 형식의 401 CMN0101 응답으로 변환한다")
    void writesUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("외부에 노출하면 안 되는 인증 예외"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())))
                .isTrue();
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body.get("code").asText()).isEqualTo("CMN0101");
        assertThat(body.get("message").asText()).isEqualTo("인증정보가 없거나 세션이 만료되었습니다.");
        assertThat(body.has("data")).isTrue();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(response.getContentAsString()).doesNotContain("외부에 노출하면 안 되는 인증 예외");
    }
}
