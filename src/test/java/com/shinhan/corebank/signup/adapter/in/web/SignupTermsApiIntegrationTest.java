package com.shinhan.corebank.signup.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.application.port.out.SignupTermsQueryPort;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class SignupTermsApiIntegrationTest extends IntegrationTestSupport {

    private static final String REDIS_KEY_PREFIX = "signup:terms-auth:";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SignupTermsQueryPort signupTermsQueryPort;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private final List<String> generatedTokens = new ArrayList<>();

    @AfterEach
    void deleteGeneratedTokens() {
        generatedTokens.forEach(token -> redisTemplate.delete(REDIS_KEY_PREFIX + token));
    }

    @Test
    @DisplayName("비로그인 고객이 최신 회원가입 약관 목록을 조회한다")
    void getSignupTermsWithoutAuthentication() throws Exception {
        List<SignupTerm> currentTerms = signupTermsQueryPort.findLatestSignupTerms();

        mockMvc.perform(get("/api/v1/auth/terms").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.items.length()").value(currentTerms.size()))
                .andExpect(jsonPath("$.data.items[0].termsId").isString());
    }

    @Test
    @DisplayName("약관 동의 검증 API는 CSRF 없이 토큰을 발급하고 Redis에 30분간 저장한다")
    void checkTermsIssuesAndStoresTermsAuthToken() throws Exception {
        List<Map<String, Object>> agreements = signupTermsQueryPort.findLatestSignupTerms().stream()
                .map(this::agreement)
                .toList();

        String requestBody = objectMapper.writeValueAsString(Map.of("agreedTerms", agreements));

        String responseBody = mockMvc.perform(post("/api/v1/auth/terms/check")
                        .contextPath("/api/v1")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        String token = response.get("data").get("termsAuthToken").asText();
        generatedTokens.add(token);

        String redisKey = REDIS_KEY_PREFIX + token;
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

        assertThat(token).startsWith("TERMS_AUTH_");
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
        assertThat(ttl).isBetween(1_795L, 1_800L);
    }

    private Map<String, Object> agreement(SignupTerm term) {
        Map<String, Object> agreement = new HashMap<>();
        agreement.put("termsId", term.termsId());
        agreement.put("version", term.version());
        agreement.put("isAgreed", true);
        agreement.put("isRead", true);
        return agreement;
    }
}
