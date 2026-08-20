package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaEntity;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaRepository;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ScheduledTransferControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Autowired
    EntityManager entityManager;

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
        mockMvc.perform(post("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.withdrawalAccountId").value(accountId))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 + CMN0002를 반환한다")
    void register_missingIdempotencyKey_returnsCmn0002() throws Exception {
        mockMvc.perform(post("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("인증 없이 등록을 요청하면 401을 반환한다")
    void register_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/scheduled-transfers")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson(accountId, "token-no-auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("fromDate/toDate 쿼리 파라미터로 조회기간을 필터링한다")
    void search_filtersByFromDateAndToDate() throws Exception {
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(5)));
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(50)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("fromDate", LocalDate.now().toString())
                        .param("toDate", LocalDate.now().plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("응답 항목은 accountNumber 필드로 상대방 계좌번호를 마스킹해서 내려준다 (payeeAccountNumber 아님)")
    void search_responseUsesAccountNumberField() throws Exception {
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(5)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("110******321"))
                .andExpect(jsonPath("$.data.items[0].payeeAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].withdrawalAccountNumber").exists())
                .andExpect(jsonPath("$.data.items[0].payeeBankName").value("신한은행"));
    }

    @Test
    @DisplayName("응답 항목은 출금계좌 별칭(fromAlias)·통장 표시내용(myPassbookMemo)·등록일시(registeredAt)를 내려준다")
    void search_includesFromAliasMemoAndRegisteredAt() throws Exception {
        entityManager.createNativeQuery("UPDATE account SET alias = :alias WHERE account_id = :accountId")
                .setParameter("alias", "우리집")
                .setParameter("accountId", accountId)
                .executeUpdate();
        ScheduledTransferJpaEntity entity = scheduledTransfer(accountId, LocalDate.now().plusDays(5));
        scheduledTransferJpaRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fromAlias").value("우리집"))
                .andExpect(jsonPath("$.data.items[0].registeredAt").exists());
    }

    @Test
    @DisplayName("출금계좌에 별칭이 없으면 fromAlias는 응답에서 null이다")
    void search_fromAliasNull_whenAccountAliasNotSet() throws Exception {
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(5)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fromAlias").value(nullValue()));
    }

    @Test
    @DisplayName("status로 필터링하면 해당 상태만 조회된다")
    void search_filtersByStatus() throws Exception {
        ScheduledTransferJpaEntity waiting = scheduledTransfer(accountId, LocalDate.now().plusDays(5));
        ScheduledTransferJpaEntity canceled = ScheduledTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(accountId)
                .payeeBankCode("088")
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(LocalDate.now().plusDays(6))
                .status(ScheduledTransferStatus.CANCELED)
                .registeredAt(LocalDateTime.now())
                .canceledAt(LocalDateTime.now())
                .build();
        scheduledTransferJpaRepository.save(waiting);
        scheduledTransferJpaRepository.save(canceled);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data.items[0].cancelable").value(true));
    }

    @Test
    @DisplayName("다른 고객의 예약이체는 조회되지 않는다 (IDOR 차단)")
    void search_otherCustomersScheduledTransfer_returnsEmptyList() throws Exception {
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        ScheduledTransferJpaEntity othersTransfer = ScheduledTransferJpaEntity.builder()
                .customerId(otherCustomerId)
                .withdrawalAccountId(otherAccountId)
                .payeeBankCode("088")
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(LocalDate.now().plusDays(5))
                .status(ScheduledTransferStatus.WAITING)
                .registeredAt(LocalDateTime.now())
                .build();
        scheduledTransferJpaRepository.save(othersTransfer);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("허용되지 않은 size로 조회하면 400 + CMN0005를 반환한다")
    void search_invalidPageSize_returnsCmn0005() throws Exception {
        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("size", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("인증 없이 조회를 요청하면 401을 반환한다")
    void search_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/scheduled-transfers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("처리결과 조회는 WAITING을 제외하고 SUCCESS/FAILED/CANCELED만 반환하며, 상단에 집계를 포함한다")
    void searchExecutionResults_excludesWaitingAndIncludesSummary() throws Exception {
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(5)));
        scheduledTransferJpaRepository.save(terminalScheduledTransfer(accountId, ScheduledTransferStatus.SUCCESS,
                LocalDate.now().minusDays(5), 10_000L, "20260805BT0000000001", null));
        scheduledTransferJpaRepository.save(terminalScheduledTransfer(accountId, ScheduledTransferStatus.FAILED,
                LocalDate.now().minusDays(3), 20_000L, null, "잔액 부족"));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("fromDate", LocalDate.now().minusDays(10).toString())
                        .param("toDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.successAmount").value(10000))
                .andExpect(jsonPath("$.data.summary.failureCount").value(1))
                .andExpect(jsonPath("$.data.summary.failureAmount").value(20000));
    }

    @Test
    @DisplayName("처리결과 조회 응답도 accountNumber 필드로 마스킹된 상대방 계좌번호를 내려준다")
    void searchExecutionResults_responseUsesAccountNumberField() throws Exception {
        scheduledTransferJpaRepository.save(terminalScheduledTransfer(accountId, ScheduledTransferStatus.SUCCESS,
                LocalDate.now().minusDays(1), 10_000L, "20260805BT0000000002", null));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers/executions")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("110******321"))
                .andExpect(jsonPath("$.data.items[0].transactionNumber").value("20260805BT0000000002"))
                // 실행일시는 executedAt으로 확정한다(api_conventions.md §6-4) - scheduledDate(예정일)로 회귀하지 않도록 필드명을 고정
                .andExpect(jsonPath("$.data.items[0].executedAt").exists())
                .andExpect(jsonPath("$.data.items[0].scheduledDate").doesNotExist());
    }

    @Test
    @DisplayName("다른 고객의 처리결과는 조회되지 않는다 (IDOR 차단)")
    void searchExecutionResults_otherCustomersScheduledTransfer_returnsEmptyList() throws Exception {
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        ScheduledTransferJpaEntity othersTransfer = ScheduledTransferJpaEntity.builder()
                .customerId(otherCustomerId)
                .withdrawalAccountId(otherAccountId)
                .payeeBankCode("088")
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(LocalDate.now().minusDays(1))
                .status(ScheduledTransferStatus.SUCCESS)
                .transactionNumber("20260805BT0000000003")
                .registeredAt(LocalDateTime.now())
                .executedAt(LocalDateTime.now())
                .build();
        scheduledTransferJpaRepository.save(othersTransfer);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers/executions")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("허용되지 않은 size로 처리결과를 조회하면 400 + CMN0005를 반환한다")
    void searchExecutionResults_invalidPageSize_returnsCmn0005() throws Exception {
        mockMvc.perform(get("/scheduled-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("size", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("인증 없이 처리결과 조회를 요청하면 401을 반환한다")
    void searchExecutionResults_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/scheduled-transfers/executions"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private String registerRequestJson(Long withdrawalAccountId, String authToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountId", withdrawalAccountId);
        body.put("depositAccountNumber", "110987654321");
        body.put("payeeName", "홍길동");
        body.put("amount", 10000);
        body.put("scheduledDate", LocalDate.now().plusDays(10).toString());
        body.put("myPassbookMemo", "내메모");
        body.put("recipientPassbookMemo", "받는메모");
        body.put("accountPasswordAuthToken", authToken);
        body.put("otpAuthToken", authToken);
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private ScheduledTransferJpaEntity scheduledTransfer(Long withdrawalAccountId, LocalDate scheduledDate) {
        return ScheduledTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .payeeBankCode("088")
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(scheduledDate)
                .status(ScheduledTransferStatus.WAITING)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    private ScheduledTransferJpaEntity terminalScheduledTransfer(Long withdrawalAccountId, ScheduledTransferStatus status,
            LocalDate scheduledDate, Long amount, String transactionNumber, String failureReason) {
        return ScheduledTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .payeeBankCode("088")
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(amount)
                .scheduledDate(scheduledDate)
                .status(status)
                .transactionNumber(transactionNumber)
                .failureReason(failureReason)
                .registeredAt(LocalDateTime.now().minusDays(30))
                .executedAt(LocalDateTime.now())
                .build();
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
