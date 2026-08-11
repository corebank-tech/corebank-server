package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SessionAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionAccessDeniedHandler deniedHandler =
            new SessionAccessDeniedHandler(objectMapper);

    @Test
    @DisplayName("권한 부족 요청을 UTF-8 JSON 형식의 403 CMN0102 응답으로 변환한다")
    void writesForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        deniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("외부에 노출하면 안 되는 권한 예외")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(response.getContentType())
        )).isTrue();
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(body.get("code").asText()).isEqualTo("CMN0102");
        assertThat(body.get("message").asText())
                .isEqualTo("해당 자원에 접근할 권한이 없습니다.");
        assertThat(body.has("data")).isTrue();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(response.getContentAsString())
                .doesNotContain("외부에 노출하면 안 되는 권한 예외");
    }
}
