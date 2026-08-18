package com.shinhan.corebank.transfer.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class FavoriteAccountControllerTest extends IntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("정상 등록 요청은 200 + ApiResponse 봉투로 등록 결과를 반환한다")
    void register_success() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.alias").value("엄마"))
                .andExpect(jsonPath("$.data.depositAccountNumber").value("110222222222"))
                .andExpect(jsonPath("$.data.transferable").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 계좌로 등록하면 200 + ApiResponse 봉투로 TRF0201 ERROR를 반환한다")
    void register_withUnknownAccount_returnsPayeeNotFound() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("999999999999", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0201"));
    }

    @Test
    @DisplayName("등록된 계좌 목록을 조회한다")
    void list_returnsRegisteredFavoriteAccounts() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].alias").value("엄마"));
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private String registerRequestJson(String depositAccountNumber, String alias) throws Exception {
        FavoriteAccountRegisterRequest request = new FavoriteAccountRegisterRequest(depositAccountNumber, alias);
        return OBJECT_MAPPER.writeValueAsString(request);
    }
}
