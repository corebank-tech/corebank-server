package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaEntity;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaRepository;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
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
class ScheduledTransferControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Autowired
    EntityManager entityManager;

    // 예약이체 등록·취소는 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
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

        verify(otpAuthTokenVerifier).verifyAndConsume(argThat(verification ->
                verification.transactionType() == OtpTransactionType.SCHEDULED_TRANSFER
                        && verification.customerId().equals(customerId)));
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
    @DisplayName("all=true면 size가 허용되지 않는 값이어도 200으로 응답하고 전체 건을 한 페이지로 반환한다")
    void search_allTrue_returnsAllMatchingRowsInOnePage() throws Exception {
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(5)));
        scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(50)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("size", "7")
                        .param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("all=true인데 결과가 100건을 초과하면 400 + CMN0006을 반환한다")
    void search_allTrue_exceedsMaxAllQuerySize_returnsCmn0006() throws Exception {
        for (int i = 0; i < 101; i++) {
            scheduledTransferJpaRepository.save(scheduledTransfer(accountId, LocalDate.now().plusDays(i + 1)));
        }
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers")
                        .with(authentication(authenticationOf(customerId)))
                        .param("all", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0006"));
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
    @DisplayName("all=true면 size가 허용되지 않는 값이어도 200으로 응답하고 전체 처리결과를 한 페이지로 반환한다")
    void searchExecutionResults_allTrue_returnsAllMatchingRowsInOnePage() throws Exception {
        scheduledTransferJpaRepository.save(terminalScheduledTransfer(accountId, ScheduledTransferStatus.SUCCESS,
                LocalDate.now().minusDays(5), 10_000L, "20260805BT0000000010", null));
        scheduledTransferJpaRepository.save(terminalScheduledTransfer(accountId, ScheduledTransferStatus.FAILED,
                LocalDate.now().minusDays(3), 20_000L, null, "잔액 부족"));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/scheduled-transfers/executions")
                        .with(authentication(authenticationOf(customerId)))
                        .param("fromDate", LocalDate.now().minusDays(10).toString())
                        .param("toDate", LocalDate.now().toString())
                        .param("size", "7")
                        .param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("인증 없이 처리결과 조회를 요청하면 401을 반환한다")
    void searchExecutionResults_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/scheduled-transfers/executions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 취소 요청은 200 + 건별 결과로 응답하고 상태가 CANCELED로 바뀐다")
    void cancel_success() throws Exception {
        ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now().plusDays(10)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/scheduled-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-1")
                        .header("Otp-Auth-Token", "otp-cancel-token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getScheduledTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.failureCount").value(0))
                .andExpect(jsonPath("$.data.items[0].scheduledTransferId").value(saved.getScheduledTransferId()))
                .andExpect(jsonPath("$.data.items[0].status").value(ProcessResultStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.items[0].canceledAt").exists());

        // OTP 거래정보는 단수 id가 아니라 id 배열로 검증돼야 한다
        verify(otpAuthTokenVerifier).verifyAndConsume(argThat(verification ->
                verification.transactionType() == OtpTransactionType.SCHEDULED_TRANSFER
                        && List.of(saved.getScheduledTransferId()).equals(verification.transactionData().get("scheduledTransferIds"))));

        entityManager.flush();
        entityManager.clear();
        assertThat(scheduledTransferJpaRepository.findById(saved.getScheduledTransferId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.CANCELED);
    }

    @Test
    @DisplayName("취소 가능한 건과 예정일 당일 건을 함께 요청하면 200으로 응답하고 건별 결과를 돌려준다")
    void cancel_partialFailure_returnsPerItemResults() throws Exception {
        ScheduledTransferJpaEntity cancelable = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now().plusDays(10)));
        ScheduledTransferJpaEntity onExecutionDate = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now()));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/scheduled-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-partial")
                        .header("Otp-Auth-Token", "otp-cancel-token-partial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(cancelable.getScheduledTransferId(), onExecutionDate.getScheduledTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.summary.failureCount").value(1))
                // items는 요청한 ID의 오름차순이다 — 순서 전제가 깨졌을 때 원인을 바로 알 수 있게 ID까지 단언한다
                .andExpect(jsonPath("$.data.items[0].scheduledTransferId").value(cancelable.getScheduledTransferId()))
                .andExpect(jsonPath("$.data.items[0].status").value(ProcessResultStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.data.items[1].scheduledTransferId").value(onExecutionDate.getScheduledTransferId()))
                .andExpect(jsonPath("$.data.items[1].status").value(ProcessResultStatus.ERROR.name()))
                .andExpect(jsonPath("$.data.items[1].failureCode").value("SCD0303"));

        entityManager.flush();
        entityManager.clear();
        assertThat(scheduledTransferJpaRepository.findById(onExecutionDate.getScheduledTransferId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.WAITING);
    }

    @Test
    @DisplayName("출금계좌가 서로 다른 건을 함께 취소하려 하면 400 + CMN0001을 반환한다 (계좌비밀번호 토큰은 계좌 하나에 묶임)")
    void cancel_mixedWithdrawalAccounts_returnsCmn0001() throws Exception {
        Long otherAccountId = insertAccount(customerId);
        ScheduledTransferJpaEntity first = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now().plusDays(10)));
        ScheduledTransferJpaEntity second = scheduledTransferJpaRepository.save(
                scheduledTransfer(otherAccountId, LocalDate.now().plusDays(10)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/scheduled-transfers/cancel")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "cancel-token-mixed")
                        .header("Otp-Auth-Token", "otp-cancel-token-mixed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(first.getScheduledTransferId(), second.getScheduledTransferId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("다른 고객이 남의 예약이체를 취소하려 하면 200 + 건별 실패(SCD0201)를 반환한다 (IDOR 차단)")
    void cancel_otherCustomersScheduledTransfer_returnsItemFailureScd0201() throws Exception {
        ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now().plusDays(10)));
        entityManager.flush();
        entityManager.clear();

        Long attackerCustomerId = insertCustomer();

        mockMvc.perform(post("/scheduled-transfers/cancel")
                        .with(authentication(authenticationOf(attackerCustomerId)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "attacker-token")
                        .header("Otp-Auth-Token", "otp-attacker-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getScheduledTransferId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].failureCode").value("SCD0201"));

        entityManager.flush();
        entityManager.clear();
        assertThat(scheduledTransferJpaRepository.findById(saved.getScheduledTransferId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.WAITING);
    }

    @Test
    @DisplayName("취소할 ID 목록이 비어 있으면 400 + CMN0002를 반환한다")
    void cancel_emptyIds_returnsCmn0002() throws Exception {
        mockMvc.perform(post("/scheduled-transfers/cancel")
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
    @DisplayName("인증 없이 취소를 요청하면 401을 반환한다")
    void cancel_withoutAuthentication_returnsUnauthorized() throws Exception {
        ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(
                scheduledTransfer(accountId, LocalDate.now().plusDays(10)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/scheduled-transfers/cancel")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "no-auth-token")
                        .header("Otp-Auth-Token", "otp-no-auth-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelRequestJson(saved.getScheduledTransferId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API 문서에 다건 취소 엔드포인트가 노출되고, 대체된 단건 취소 경로는 사라진다 (api_conventions.md §6-7)")
    void apiDocs_exposesMultiCancelEndpointOnly() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/scheduled-transfers/cancel']['post']['operationId']")
                        .value("cancelScheduledTransfers"))
                .andExpect(jsonPath("$['paths']['/scheduled-transfers/{scheduledTransferId}/cancel']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelRequest']"
                        + "['properties']['scheduledTransferIds']['type']").value("array"))
                // 배열 제약과 필수 여부가 스키마에 드러나야 FE codegen이 그대로 쓸 수 있다
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelRequest']"
                        + "['properties']['scheduledTransferIds']['minItems']").value(1))
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelRequest']"
                        + "['properties']['scheduledTransferIds']['maxItems']").value(50))
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelRequest']['required']")
                        .value(hasItem("scheduledTransferIds")))
                // 성공 건의 failureCode·실패 건의 canceledAt은 null로 내려가므로 스키마도 null을 허용해야 한다
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelItemResponse']"
                        + "['properties']['canceledAt']['type']").value(hasItem("null")))
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelItemResponse']"
                        + "['properties']['failureCode']['type']").value(hasItem("null")))
                .andExpect(jsonPath("$['components']['schemas']['ScheduledTransferCancelItemResponse']"
                        + "['properties']['failureReason']['type']").value(hasItem("null")))
                // 저장 단계 동시 변경은 낙관적 락에 걸려 CMN0303으로 나간다 (PR #335 리뷰 R2)
                .andExpect(jsonPath("$['paths']['/scheduled-transfers/cancel']['post']"
                        + "['responses']['409']['description']").value(containsString("CMN0303")));
    }

    private String cancelRequestJson(Long... scheduledTransferIds) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheduledTransferIds", List.of(scheduledTransferIds));
        return OBJECT_MAPPER.writeValueAsString(body);
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
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', TRUE, NOW(), NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
