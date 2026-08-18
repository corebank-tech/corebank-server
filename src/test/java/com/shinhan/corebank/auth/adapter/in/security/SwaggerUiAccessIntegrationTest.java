package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    @DisplayName("보호 API는 여전히 세션 없이 접근할 수 없다")
    void stillRejectsProtectedApiWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }
}
