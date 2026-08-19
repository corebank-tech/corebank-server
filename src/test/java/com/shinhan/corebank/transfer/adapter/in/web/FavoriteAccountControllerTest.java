package com.shinhan.corebank.transfer.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("999999999999", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0201"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void register_withoutIdempotencyKey_returnsRequiredFieldMissing() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 같은 요청을 두 번 보내면 등록은 1건만 생기고 동일한 응답을 재생한다")
    void register_sameIdempotencyKeyTwice_repliesWithSameResponseAndSingleRow() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("엄마"));

        // completeIfProcessing()은 JPQL 벌크 UPDATE라 1차 캐시에 이미 로드된 엔티티는 자동 갱신 안 됨.
        // 두 HTTP 호출이 테스트 트랜잭션 하나로 묶여 있어서 생기는 문제라 clear()로 캐시를 비운다(운영 환경은 요청마다 별도 세션).
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("엄마"));

        mockMvc.perform(get("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L))))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("같은 Idempotency-Key인데 요청 내용(별칭)이 다르면 409 + CMN0302를 반환한다")
    void register_sameIdempotencyKeyDifferentBody_returnsIdempotencyKeyReused() throws Exception {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "엄마")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transfers/favorite-accounts")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson("110222222222", "아빠")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
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
                        .header("Idempotency-Key", UUID.randomUUID().toString())
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
