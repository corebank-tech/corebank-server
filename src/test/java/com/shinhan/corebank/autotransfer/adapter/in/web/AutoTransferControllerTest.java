package com.shinhan.corebank.autotransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class AutoTransferControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AutoTransferJpaRepository autoTransferJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Long customerId;
    private Long accountId;

    @BeforeEach
    void seedCustomerAndAccount() {
        customerId = insertCustomer();
        accountId = insertAccount(customerId);
    }

    @Test
    @DisplayName("정상 등록 요청은 200 + ApiResponse 봉투로 응답한다")
    void register_success() throws Exception {
        mockMvc.perform(post("/auto-transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(customerId, accountId, "token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(accountId))
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.status").value("NORMAL"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void register_missingIdempotencyKey_returnsCmn0002() throws Exception {
        mockMvc.perform(post("/auto-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(customerId, accountId, "token-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 같은 요청을 두 번 보내면 등록은 1건만 생기고 동일한 응답을 재생한다")
    void register_sameIdempotencyKeyTwice_registersOnce() throws Exception {
        String requestJson = registerRequestJson(customerId, accountId, "token-3");
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        // completeIfProcessing()은 JPQL 벌크 UPDATE라 1차 캐시에 이미 로드된 엔티티는 자동 갱신 안 됨.
        // 테스트가 두 HTTP 호출을 하나의 트랜잭션(세션)으로 묶고 있어서 생기는 문제라 clear()로 캐시를 비움.
        // (운영에서는 요청마다 별도 세션이라 해당 없음)
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(accountId));

        assertThat(autoTransferJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key인데 요청 내용이 다르면 409 + CMN0302를 반환한다")
    void register_sameKeyDifferentBody_returnsCmn0302() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(customerId, accountId, "token-4")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auto-transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(customerId, accountId, "different-token")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    private String registerRequestJson(Long customerId, Long withdrawalAccountId, String authToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("withdrawalAccountId", withdrawalAccountId);
        body.put("depositAccountNumber", "110987654321");
        body.put("payeeName", "홍길동");
        body.put("amount", 10000);
        body.put("cycleMonths", 1);
        body.put("transferDay", 15);
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusMonths(12).toString());
        body.put("myPassbookMemo", "내메모");
        body.put("recipientPassbookMemo", "받는메모");
        body.put("authToken", authToken);
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private Long insertCustomer() {
        String userId = "u" + System.nanoTime();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', 'test@test.com', '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", System.nanoTime() % 1_000_000_000_000L);
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}