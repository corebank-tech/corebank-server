package com.shinhan.corebank.autotransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaRepository;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    AutoTransferExecutionJpaRepository autoTransferExecutionJpaRepository;

    @Autowired
    EntityManager entityManager;

    // 자동이체 등록/변경/해지는 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    OtpAuthTokenVerifier otpAuthTokenVerifier;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

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
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(accountId))
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.status").value("NORMAL"));
    }

    @Test
    @DisplayName("amount가 음수면 400 + AUT0008을 반환한다")
    void register_negativeAmount_returnsAut0008() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountId", accountId);
        body.put("depositAccountNumber", "110987654321");
        body.put("payeeName", "홍길동");
        body.put("amount", -100);
        body.put("cycleMonths", 1);
        body.put("transferDay", 15);
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusMonths(12).toString());
        body.put("accountPasswordAuthToken", "token-negative-amount");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUT0008"));
    }

    @Test
    @DisplayName("실패한 요청을 같은 Idempotency-Key로 재시도하면 PROCESSING에 갇히지 않고 다시 평가된다")
    void register_failedRequest_canBeRetriedWithSameKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountId", accountId);
        body.put("depositAccountNumber", "110987654321");
        body.put("payeeName", "홍길동");
        body.put("amount", -100);
        body.put("cycleMonths", 1);
        body.put("transferDay", 15);
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusMonths(12).toString());
        body.put("accountPasswordAuthToken", "retry-token");
        body.put("otpAuthToken", "otp-token");
        String json = OBJECT_MAPPER.writeValueAsString(body);

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUT0008"));

        entityManager.flush();
        entityManager.clear();

        // release()가 없었다면 이 두 번째 요청은 CMN0303(처리 중)을 받았을 것 — 여전히 원래 오류(AUT0008)가 나야 정상
        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUT0008"));
    }

    @Test
    @DisplayName("cycleMonths가 1/3/6이 아니면 400 + AUT0007을 반환한다")
    void register_invalidCycleMonths_returnsAut0007() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountId", accountId);
        body.put("depositAccountNumber", "110987654321");
        body.put("payeeName", "홍길동");
        body.put("amount", 10000);
        body.put("cycleMonths", 2);
        body.put("transferDay", 15);
        body.put("startDate", LocalDate.now().plusDays(10).toString());
        body.put("endDate", LocalDate.now().plusMonths(12).toString());
        body.put("accountPasswordAuthToken", "token-invalid-cycle");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUT0007"));
    }

    @Test
    @DisplayName("Idempotency-Key가 UUID 형식이 아니면 500 대신 400 + CMN0001을 반환한다")
    void register_invalidIdempotencyKeyFormat_returnsCmn0001() throws Exception {
        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-bad-key")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void register_missingIdempotencyKey_returnsCmn0002() throws Exception {
        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 같은 요청을 두 번 보내면 등록은 1건만 생기고 동일한 응답을 재생한다")
    void register_sameIdempotencyKeyTwice_registersOnce() throws Exception {
        String requestJson = registerRequestJson(accountId, "token-3");
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
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
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(accountId));

        assertThat(autoTransferJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key인데 요청 내용(금액)이 다르면 409 + CMN0302를 반환한다")
    void register_sameKeyDifferentBody_returnsCmn0302() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-4")))
                .andExpect(status().isOk());

        Map<String, Object> differentAmountBody = new LinkedHashMap<>();
        differentAmountBody.put("withdrawalAccountId", accountId);
        differentAmountBody.put("depositAccountNumber", "110987654321");
        differentAmountBody.put("payeeName", "홍길동");
        differentAmountBody.put("amount", 99999);
        differentAmountBody.put("cycleMonths", 1);
        differentAmountBody.put("transferDay", 15);
        differentAmountBody.put("startDate", LocalDate.now().plusDays(10).toString());
        differentAmountBody.put("endDate", LocalDate.now().plusMonths(12).toString());
        differentAmountBody.put("myPassbookMemo", "내메모");
        differentAmountBody.put("recipientPassbookMemo", "받는메모");
        differentAmountBody.put("accountPasswordAuthToken", "token-4");
        differentAmountBody.put("otpAuthToken", "otp-token");

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(differentAmountBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 인증 토큰만 다르게 보내면 충돌이 아니라 재생된다 (OTP 재발급 후 재시도 대응)")
    void register_sameKeyDifferentAuthTokenOnly_repliesWithoutConflict() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-original")))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-reissued")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        assertThat(autoTransferJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증 없이 등록을 요청하면 401을 반환한다")
    void register_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auto-transfers")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-no-auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 고객이 로그인한 상태로 남의 출금계좌로 자동이체를 등록하려 하면 404 + AUT0202를 반환한다 (IDOR 차단)")
    void register_otherCustomersWithdrawalAccount_returnsAut0202() throws Exception {
        Long attackerCustomerId = insertCustomer();

        // accountId는 @BeforeEach에서 customerId(피해자) 소유로 만들어짐 — attackerCustomerId 세션으로 그 계좌를 자기 것인 양 등록 시도
        mockMvc.perform(post("/auto-transfers")
                        .with(authentication(authenticationOf(attackerCustomerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "attacker-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUT0202"));
    }

    @Test
    @DisplayName("출금계좌ID로 조회하면 등록해둔 자동이체가 목록에 나온다")
    void search_returnsRegisteredAutoTransfers() throws Exception {
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000001", AutoTransferStatus.NORMAL, 10));
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000002", AutoTransferStatus.TERMINATED, 11));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("응답에 출금계좌 별칭(fromAlias)과 등록일시(registeredAt)가 포함된다")
    void search_includesFromAliasAndRegisteredAt() throws Exception {
        entityManager.createNativeQuery("UPDATE account SET alias = :alias WHERE account_id = :accountId")
                .setParameter("alias", "월세계좌")
                .setParameter("accountId", accountId)
                .executeUpdate();
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000005", AutoTransferStatus.NORMAL, 14));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fromAlias").value("월세계좌"))
                .andExpect(jsonPath("$.data.items[0].registeredAt").exists());
    }

    @Test
    @DisplayName("출금계좌 별칭이 없으면 fromAlias는 null로 응답된다")
    void search_withoutAlias_fromAliasIsNull() throws Exception {
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000006", AutoTransferStatus.NORMAL, 16));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fromAlias").value(nullValue()));
    }

    @Test
    @DisplayName("status로 필터링하면 해당 상태만 조회된다")
    void search_filtersByStatus() throws Exception {
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000003", AutoTransferStatus.NORMAL, 12));
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000004", AutoTransferStatus.TERMINATED, 13));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId))
                        .param("status", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("NORMAL"));
    }

    @Test
    @DisplayName("status=ALL이면 필터 없이 전체 조회된다")
    void search_statusAll_returnsAllStatuses() throws Exception {
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000014", AutoTransferStatus.NORMAL, 25));
        autoTransferJpaRepository.save(autoTransfer(accountId, "110000000015", AutoTransferStatus.TERMINATED, 26));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId))
                        .param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("status가 도메인에 없는 값이면 400 + CMN0001을 반환한다")
    void search_invalidStatus_returnsCmn0001() throws Exception {
        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId))
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("다른 고객의 출금계좌ID로 조회하면 빈 목록을 반환한다 (타 고객 접근 차단)")
    void search_otherCustomersWithdrawalAccount_returnsEmptyList() throws Exception {
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        autoTransferJpaRepository.save(autoTransfer(otherCustomerId, otherAccountId, "110000000012", AutoTransferStatus.NORMAL, 23));
        entityManager.flush();
        entityManager.clear();

        // customerId(나)로 로그인한 상태에서 남의 출금계좌ID(otherAccountId)로 조회를 시도해도
        // customer_id·withdrawal_account_id AND 조건에 걸려 빈 목록이어야 한다.
        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(otherAccountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("withdrawalAccountId가 없으면 400을 반환한다")
    void search_missingWithdrawalAccountId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/auto-transfers")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 조회를 요청하면 401을 반환한다")
    void search_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auto-transfers")
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 결과조회 요청은 200으로 응답하고 정상/오류 이력과 집계가 함께 내려온다")
    void searchExecutionHistory_success() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000050", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();

        LocalDate today = LocalDate.now();
        autoTransferExecutionJpaRepository.save(execution(saved, today, ProcessResultStatus.SUCCESS, 10000L, "TXN0010", null));
        autoTransferExecutionJpaRepository.save(execution(saved, today.minusDays(1), ProcessResultStatus.ERROR, 5000L, null, "잔액부족"));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[1].status").value("ERROR"))
                .andExpect(jsonPath("$.data.items[1].failureReason").value("잔액부족"))
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.successAmount").value(10000))
                .andExpect(jsonPath("$.data.summary.errorCount").value(1))
                .andExpect(jsonPath("$.data.summary.errorAmount").value(5000));
    }

    @Test
    @DisplayName("조회기간 밖의 회차는 조회되지 않는다")
    void searchExecutionHistory_excludesExecutionsOutsidePeriod() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000052", AutoTransferStatus.NORMAL, 12));
        entityManager.flush();
        LocalDate inRange = LocalDate.now();
        LocalDate outOfRange = LocalDate.now().minusMonths(2);
        autoTransferExecutionJpaRepository.save(execution(saved, inRange, ProcessResultStatus.SUCCESS, 10000L, "TXN0012", null));
        autoTransferExecutionJpaRepository.save(execution(saved, outOfRange, ProcessResultStatus.SUCCESS, 20000L, "TXN0013", null));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(accountId))
                        .param("fromDate", inRange.minusDays(1).toString())
                        .param("toDate", inRange.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.summary.successAmount").value(10000));
    }

    @Test
    @DisplayName("다른 고객의 출금계좌ID로 결과조회하면 빈 목록을 반환한다 (타 고객 접근 차단)")
    void searchExecutionHistory_otherCustomersWithdrawalAccount_returnsEmpty() throws Exception {
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        AutoTransferJpaEntity otherAutoTransfer = autoTransferJpaRepository.save(
                autoTransfer(otherCustomerId, otherAccountId, "110000000051", AutoTransferStatus.NORMAL, 11));
        entityManager.flush();
        autoTransferExecutionJpaRepository.save(execution(otherAutoTransfer, LocalDate.now(), ProcessResultStatus.SUCCESS, 10000L, "TXN0011", null));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/auto-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("withdrawalAccountId", String.valueOf(otherAccountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("withdrawalAccountId가 없으면 400을 반환한다")
    void searchExecutionHistory_missingWithdrawalAccountId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/auto-transfers/executions")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 결과조회를 요청하면 401을 반환한다")
    void searchExecutionHistory_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auto-transfers/executions")
                        .param("withdrawalAccountId", String.valueOf(accountId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 변경 요청은 200으로 응답하고 변경된 값이 반영된다")
    void change_success() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000005", AutoTransferStatus.NORMAL, 15));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequestJson("change-token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.amount").value(20000))
                .andExpect(jsonPath("$.data.cycleMonths").value(3));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 불변 필드(withdrawalAccountId)만 추가해서 재요청하면 재생 대신 409 + CMN0302를 반환한다")
    void change_sameKeyWithUnmodifiableFieldAdded_returnsCmn0302() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000016", AutoTransferStatus.NORMAL, 27));
        entityManager.flush();
        entityManager.clear();
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequestJson("change-token-fp")))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 20000);
        body.put("cycleMonths", 3);
        body.put("endDate", LocalDate.now().plusYears(2).toString());
        body.put("myPassbookMemo", "새메모");
        body.put("recipientPassbookMemo", "새받는메모");
        body.put("withdrawalAccountId", accountId); // 불변 필드를 슬쩍 추가 — fingerprint가 이걸 반영해야 함
        body.put("accountPasswordAuthToken", "change-token-fp");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("변경 금액이 1회 이체한도를 초과하면 400 + LMT0002를 반환한다")
    void change_amountExceedsOneTimeLimit_returnsLmt0002() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000013", AutoTransferStatus.NORMAL, 24));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 20_000_000);
        body.put("accountPasswordAuthToken", "change-token-limit");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LMT0002"));
    }

    @Test
    @DisplayName("존재하지 않는 자동이체를 변경하려 하면 404 + AUT0201을 반환한다")
    void change_notFound_returnsAut0201() throws Exception {
        mockMvc.perform(patch("/auto-transfers/{id}", 999_999L)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequestJson("change-token-2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUT0201"));
    }

    @Test
    @DisplayName("일부 필드만 보내면 나머지는 기존 값 그대로 유지된다")
    void change_partialFields_keepsRestUnchanged() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000010", AutoTransferStatus.NORMAL, 20));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 15000);
        body.put("accountPasswordAuthToken", "change-token-partial");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(15000))
                .andExpect(jsonPath("$.data.cycleMonths").value(1));
    }

    @Test
    @DisplayName("withdrawalAccountId 변경을 시도하면 400 + AUT0003을 반환한다")
    void change_withdrawalAccountId_returnsAut0003() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000011", AutoTransferStatus.NORMAL, 22));
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountId", 999999L);
        body.put("accountPasswordAuthToken", "change-token-unmodifiable");
        body.put("otpAuthToken", "otp-token");

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUT0003"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void change_missingIdempotencyKey_returnsCmn0002() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000006", AutoTransferStatus.NORMAL, 16));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequestJson("change-token-3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("다른 고객이 로그인한 상태로 남의 자동이체를 변경하려 하면 404 + AUT0201을 반환한다 (IDOR 차단)")
    void change_otherCustomersAutoTransfer_returnsAut0201() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000018", AutoTransferStatus.NORMAL, 29));
        entityManager.flush();
        entityManager.clear();

        Long attackerCustomerId = insertCustomer();

        mockMvc.perform(patch("/auto-transfers/{id}", saved.getAutoTransferId())
                        .with(authentication(authenticationOf(attackerCustomerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeRequestJson("attacker-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUT0201"));
    }

    @Test
    @DisplayName("정상 해지 요청은 200으로 응답하고 상태가 TERMINATED로 바뀐다")
    void cancel_success() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000007", AutoTransferStatus.NORMAL, 17));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-1")
                        .header("Otp-Auth-Token", "otp-cancel-token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.failureCount").value(0))
                .andExpect(jsonPath("$.data.items[0].autoTransferId").value(saved.getAutoTransferId()))
                .andExpect(jsonPath("$.data.items[0].status").value(ProcessResultStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.items[0].failureCode").value(nullValue()));

        entityManager.flush();
        entityManager.clear();
        AutoTransferJpaEntity found = autoTransferJpaRepository.findById(saved.getAutoTransferId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(AutoTransferStatus.TERMINATED);
    }

    @Test
    @DisplayName("해지 가능한 건과 불가능한 건을 함께 요청하면 200으로 응답하고 건별 결과를 돌려준다")
    void cancel_partialFailure_returnsPerItemResults() throws Exception {
        AutoTransferJpaEntity normal = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000021", AutoTransferStatus.NORMAL, 21));
        // 기간 만료로 종료된 건은 고객이 해지한 적이 없으므로 멱등 성공이 아니라 AUT0302 실패다
        AutoTransferJpaEntity expired = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000022", AutoTransferStatus.EXPIRED, 22));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-partial")
                        .header("Otp-Auth-Token", "otp-cancel-token-partial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(normal.getAutoTransferId(), expired.getAutoTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.failureCount").value(1))
                // items는 요청한 ID의 오름차순이고, 두 건은 같은 순서로 저장돼 normal이 먼저다
                .andExpect(jsonPath("$.data.items[0].status").value(ProcessResultStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.items[1].status").value(ProcessResultStatus.ERROR.name()))
                .andExpect(jsonPath("$.data.items[1].failureCode").value("AUT0302"));

        entityManager.flush();
        entityManager.clear();
        assertThat(autoTransferJpaRepository.findById(normal.getAutoTransferId()).orElseThrow().getStatus())
                .isEqualTo(AutoTransferStatus.TERMINATED);
    }

    @Test
    @DisplayName("존재하지 않는 자동이체를 해지하려 하면 200 + 건별 실패(AUT0201)로 반환한다")
    void cancel_notFound_returnsItemFailureAut0201() throws Exception {
        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-2")
                        .header("Otp-Auth-Token", "otp-cancel-token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(999_999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.failureCount").value(1))
                .andExpect(jsonPath("$.data.items[0].failureCode").value("AUT0201"));
    }

    @Test
    @DisplayName("해지할 ID 목록이 비어 있으면 400 + CMN0002를 반환한다")
    void cancel_emptyIds_returnsCmn0002() throws Exception {
        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-empty")
                        .header("Otp-Auth-Token", "otp-cancel-token-empty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void cancel_missingIdempotencyKey_returnsCmn0002() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000008", AutoTransferStatus.NORMAL, 18));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Account-Password-Auth-Token", "cancel-token-3")
                        .header("Otp-Auth-Token", "otp-cancel-token-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 두 번 해지 요청을 보내면 재생되고 상태 변경은 1번만 일어난다")
    void cancel_sameIdempotencyKeyTwice_repliesWithoutReapplying() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000009", AutoTransferStatus.NORMAL, 19));
        entityManager.flush();
        entityManager.clear();
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Account-Password-Auth-Token", "cancel-token-4")
                        .header("Otp-Auth-Token", "otp-cancel-token-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Account-Password-Auth-Token", "cancel-token-4")
                        .header("Otp-Auth-Token", "otp-cancel-token-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.summary.successCount").value(1));
    }

    @Test
    @DisplayName("다른 고객이 로그인한 상태로 남의 자동이체를 해지하려 하면 200 + 건별 실패(AUT0201)를 반환한다 (IDOR 차단)")
    void cancel_otherCustomersAutoTransfer_returnsItemFailureAut0201() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000017", AutoTransferStatus.NORMAL, 28));
        entityManager.flush();
        entityManager.clear();

        Long attackerCustomerId = insertCustomer();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(authentication(authenticationOf(attackerCustomerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "attacker-token")
                        .header("Otp-Auth-Token", "otp-attacker-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].failureCode").value("AUT0201"));

        entityManager.flush();
        entityManager.clear();
        assertThat(autoTransferJpaRepository.findById(saved.getAutoTransferId()).orElseThrow().getStatus())
                .isEqualTo(AutoTransferStatus.NORMAL);
    }

    @Test
    @DisplayName("인증 없이 해지를 요청하면 401을 반환한다")
    void cancel_withoutAuthentication_returnsUnauthorized() throws Exception {
        AutoTransferJpaEntity saved = autoTransferJpaRepository.save(
                autoTransfer(accountId, "110000000019", AutoTransferStatus.NORMAL, 30));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/auto-transfers/cancel")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "no-auth-token")
                        .header("Otp-Auth-Token", "otp-no-auth-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getAutoTransferId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API 문서에 다건 해지 엔드포인트가 노출되고, 대체된 단건 해지 경로의 DELETE는 사라진다 (api_conventions.md §6-7)")
    void apiDocs_exposesMultiCancelEndpointOnly() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/auto-transfers/cancel']['post']['operationId']")
                        .value("cancelAutoTransfers"))
                .andExpect(jsonPath("$['paths']['/auto-transfers/{autoTransferId}']['delete']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['AutoTransferCancelRequest']"
                        + "['properties']['autoTransferIds']['type']").value("array"));
    }

    private String cancelRequestJson(Long... autoTransferIds) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("autoTransferIds", List.of(autoTransferIds));
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private String changeRequestJson(String authToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 20000);
        body.put("cycleMonths", 3);
        body.put("endDate", LocalDate.now().plusYears(2).toString());
        body.put("myPassbookMemo", "새메모");
        body.put("recipientPassbookMemo", "새받는메모");
        body.put("accountPasswordAuthToken", authToken);
        body.put("otpAuthToken", "otp-token");
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private AutoTransferJpaEntity autoTransfer(Long withdrawalAccountId, String depositAccountNumber, AutoTransferStatus status, int transferDay) {
        return autoTransfer(customerId, withdrawalAccountId, depositAccountNumber, status, transferDay);
    }

    private AutoTransferJpaEntity autoTransfer(Long ownerCustomerId, Long withdrawalAccountId, String depositAccountNumber, AutoTransferStatus status, int transferDay) {
        return AutoTransferJpaEntity.builder()
                .customerId(ownerCustomerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(transferDay)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .nextExecutionDate(LocalDate.of(2026, 1, transferDay))
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(status)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private AutoTransferExecutionJpaEntity execution(AutoTransferJpaEntity autoTransfer, LocalDate executionDate,
            ProcessResultStatus status, Long amount, String transactionNumber, String failureReason) {
        return AutoTransferExecutionJpaEntity.builder()
                .autoTransfer(autoTransfer)
                .executionDate(executionDate)
                .amount(amount)
                .status(status)
                .transactionNumber(transactionNumber)
                .failureReason(failureReason)
                .executedAt(executionDate.atStartOfDay())
                .build();
    }

    private String registerRequestJson(Long withdrawalAccountId, String authToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
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
        body.put("accountPasswordAuthToken", authToken);
        body.put("otpAuthToken", "otp-token");
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private Long insertCustomer() {
        // 한 테스트 안에서 두 번째 고객을 만들 때 System.nanoTime()이 짧은 간격에선 값이 겹칠 수 있어 카운터로 유일성을 보장한다(user_id는 VARCHAR(20), email은 UNIQUE)
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    // withdrawal_registered=TRUE로 채운다 - 등록 시 출금계좌 등록 여부 검증(#234)이 이 값을 확인하므로,
    // 별도로 미등록 케이스를 검증하는 테스트가 아니면 정상 출금계좌로 취급돼야 한다
    private Long insertAccount(Long customerId) {
        // System.nanoTime() 기반 생성은 짧은 간격의 연속 호출에서 겹칠 수 있어 카운터로 유일성을 보장한다(uk_account_number)
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', TRUE, NOW(), NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
