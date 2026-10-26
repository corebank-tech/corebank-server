package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 클래스 레벨 @Transactional을 두지 않는다(TransferExecutionServiceTest와 동일 이유) —
 * POST /transfers는 내부적으로 TransferExecutionService.execute()의 REQUIRES_NEW 트랜잭션에서
 * SELECT FOR UPDATE로 계좌 락을 잡는데, 픽스처를 테스트 트랜잭션 안에서만 flush하면 커밋되지
 * 않은 행이라 별도 커넥션의 락 획득이 타임아웃(CannotAcquireLockException)난다.
 */
@AutoConfigureMockMvc
class TransferControllerTest extends IntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 이체 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    // 계좌비밀번호 인증도 동일하게 account.api 경계만 검증한다 — 토큰 자체의 발급/소비 로직은
    // account 도메인 테스트가 담당한다.
    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101");
        jdbcTemplate.update("DELETE FROM transfer WHERE transaction_number IN "
                + "('20260810IT0000000010','20260810IT0000000011','20260810IT0000000012')");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update(
                """
                DELETE e FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
                WHERE v.tx_type IN ('INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT')
                """);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE tx_type IN ('INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT')");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    @Test
    @DisplayName("정상 이체 요청은 200 + ApiResponse 봉투로 SUCCESS 결과를 반환한다")
    void execute_success() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(post("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "dummy-auth-token")
                        .header("Otp-Auth-Token", "dummy-otp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(101L, "110222222222", 30000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.withdrawalBalanceAfter").value(70000));
    }

    @Test
    @DisplayName("출금계좌와 입금계좌가 같으면 200 + ApiResponse 봉투로 TRF0002 ERROR 결과를 반환한다")
    void execute_withSameWithdrawalAndDepositAccount_returnsErrorResult() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(post("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "dummy-auth-token")
                        .header("Otp-Auth-Token", "dummy-otp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(101L, "110111111111", 30000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("ERROR"))
                .andExpect(jsonPath("$.data.errorCode").value("TRF0002"));
    }

    @Test
    @DisplayName("인증 없이 이체를 요청하면 401을 반환한다")
    void execute_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/transfers")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "dummy-auth-token")
                        .header("Otp-Auth-Token", "dummy-otp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(101L, "110222222222", 30000L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하는 계좌번호로 예금주를 조회하면 200 + 예금주명을 반환한다")
    void inquirePayee_success() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(get("/transfers/payee")
                        .with(authentication(authenticationOf(1L)))
                        .param("accountNumber", "110222222222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.accountId").value(202))
                .andExpect(jsonPath("$.data.payeeName").value("테스터"));
    }

    @Test
    @DisplayName("존재하지 않는 계좌번호로 예금주를 조회하면 404 + TRF0201을 반환한다")
    void inquirePayee_notFound_returnsTrf0201() throws Exception {
        mockMvc.perform(get("/transfers/payee")
                        .with(authentication(authenticationOf(1L)))
                        .param("accountNumber", "999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0201"));
    }

    @Test
    @DisplayName("이체결과 목록 조회: 본인 계좌면 200 + 집계·항목을 반환한다")
    void searchHistory_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000010', 101, 202, '110222222222', '성춘향', 10000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").exists())
                .andExpect(jsonPath("$.data.summary.successCount").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionNumber").value("20260810IT0000000010"))
                .andExpect(jsonPath("$.data.items[0].payeeName").value("성*향"))
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("110******222"));
    }

    @Test
    @DisplayName("이체결과 목록 조회: all=true면 size가 허용되지 않는 값이어도 200으로 응답하고 전체 건을 한 페이지로 반환한다")
    void searchHistory_allTrue_returnsAllMatchingRowsInOnePage() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000012', 101, 202, '110222222222', '성춘향', 10000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31")
                        .param("size", "7")
                        .param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("이체결과 목록 조회: 남의 출금계좌면 400 + TRF0001을 반환한다")
    void searchHistory_notOwned_returnsTrf0001() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(2L)))
                        .param("withdrawalAccountId", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0001"));
    }

    @Test
    @DisplayName("이체결과 CSV 다운로드: 본인 계좌면 200 + text/csv 파일로 목록 조회와 동일한 값이 내려간다")
    void exportCsv_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000015', 101, 202, '110222222222', '성춘향', 10000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        MvcResult result = mockMvc.perform(get("/transfers/export")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transfer-history.csv\""))
                .andReturn();

        byte[] raw = result.getResponse().getContentAsByteArray();
        String body = new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8); // 앞 3바이트는 UTF-8 BOM
        assertThat(body).contains("거래번호,처리상태,처리일시,입금계좌번호,예금주명,금액,이체구분,채널,오류코드,실패사유");
        assertThat(body).contains("20260810IT0000000015,SUCCESS");
        assertThat(body).contains("110******222,성*향,10000");
    }

    @Test
    @DisplayName("이체결과 CSV 다운로드: 남의 출금계좌면 CSV 대신 400 + JSON 오류(TRF0001)를 반환한다")
    void exportCsv_notOwned_returnsTrf0001() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(get("/transfers/export")
                        .with(authentication(authenticationOf(2L)))
                        .param("withdrawalAccountId", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.code").value("TRF0001"));
    }

    @Test
    @DisplayName("이체결과 상세 조회: 본인 거래면 200 + 상세 정보를 반환한다")
    void getHistoryDetail_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000011', 101, 202, '110222222222', '성춘향', 20000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        mockMvc.perform(get("/transfers/20260810IT0000000011").with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.transactionNumber").value("20260810IT0000000011"))
                .andExpect(jsonPath("$.data.amount").value(20000))
                .andExpect(jsonPath("$.data.payeeName").value("성*향"))
                .andExpect(jsonPath("$.data.accountNumber").value("110******222"));
    }

    // 예금주를 받지 못한 타행 건이 섞여도 상세·목록이 깨지지 않아야 한다(interbank_transfer_fe_handoff.md §7-1).
    private void insertInterbankTimeoutTransfer() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_bank_code, payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810WB0000000020', 101, NULL, '1234567890123', '004', NULL, 50000, 0, 'IMMEDIATE', 'WB',
                    'TIMEOUT', '2026-08-10 10:00:00', NOW(6))
            """)
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("이체결과 상세 조회: 예금주가 없는 타행 건은 payeeName null, 은행코드와 13자리 마스킹 계좌번호를 반환한다")
    void getHistoryDetail_interbankWithoutPayeeName() throws Exception {
        insertInterbankTimeoutTransfer();

        mockMvc.perform(get("/transfers/20260810WB0000000020").with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TIMEOUT"))
                .andExpect(jsonPath("$.data.payeeName").value(nullValue()))
                .andExpect(jsonPath("$.data.payeeBankCode").value("004"))
                .andExpect(jsonPath("$.data.accountNumber").value("123*******123"));
    }

    @Test
    @DisplayName("이체결과 목록 조회: 예금주가 없는 타행 건이 있어도 200 + payeeName null로 반환한다")
    void searchHistory_interbankWithoutPayeeName() throws Exception {
        insertInterbankTimeoutTransfer();

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].transactionNumber").value("20260810WB0000000020"))
                .andExpect(jsonPath("$.data.items[0].payeeName").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("123*******123"));
    }

    @Test
    @DisplayName("이체결과 상세 조회: 존재하지 않는 거래번호면 404 + TRF0202를 반환한다")
    void getHistoryDetail_notFound_returnsTrf0202() throws Exception {
        mockMvc.perform(get("/transfers/NOT-EXIST").with(authentication(authenticationOf(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0202"));
    }

    @Test
    @DisplayName("이체결과 상세 조회: 남의 거래면 404 + TRF0202를 반환한다")
    void getHistoryDetail_notOwned_returnsTrf0202() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000012', 101, 202, '110222222222', '성춘향', 30000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        mockMvc.perform(get("/transfers/20260810IT0000000012").with(authentication(authenticationOf(2L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0202"));
    }

    @Test
    @DisplayName("월별 통계: 본인 계좌면 200 + 해당 연월 성공/실패 건수·금액을 반환한다")
    void monthlyStatistics_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20260810IT0000000020', 101, 202, '110222222222', '성춘향', 10000, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                    '2026-08-10 09:00:00', NOW(6))
                ON DUPLICATE KEY UPDATE transaction_number = transaction_number
            """)
                    .executeUpdate();
        });

        mockMvc.perform(get("/transfers/monthly-statistics")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("yearMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.successAmount").value(10000))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    @Test
    @DisplayName("월별 통계: 남의 출금계좌면 400 + TRF0001을 반환한다")
    void monthlyStatistics_notOwned_returnsTrf0001() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(get("/transfers/monthly-statistics")
                        .with(authentication(authenticationOf(2L)))
                        .param("withdrawalAccountId", "101")
                        .param("yearMonth", "2026-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0001"));
    }

    @Test
    @DisplayName("월별 통계: yearMonth 형식이 올바르지 않으면 400 + CMN0001을 반환한다")
    void monthlyStatistics_invalidYearMonth_returnsInvalidInput() throws Exception {
        mockMvc.perform(get("/transfers/monthly-statistics")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("yearMonth", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private String transferRequestJson(Long withdrawalAccountId, String depositAccountNumber, long amount)
            throws Exception {
        TransferRequest request =
                new TransferRequest(withdrawalAccountId, depositAccountNumber, amount, "출금메모", "입금메모");
        return OBJECT_MAPPER.writeValueAsString(request);
    }

    private String interbankRequestJson(String bankCode, String depositAccountNumber, String recipientMemo)
            throws Exception {
        return OBJECT_MAPPER.writeValueAsString(
                new InterbankTransferRequest(101L, bankCode, depositAccountNumber, 30000L, "생활비", recipientMemo));
    }

    private org.springframework.test.web.servlet.ResultActions postInterbank(String body) throws Exception {
        return mockMvc.perform(post("/transfers/interbank")
                .with(authentication(authenticationOf(1L)))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Account-Password-Auth-Token", "dummy-auth-token")
                .header("Otp-Auth-Token", "dummy-otp-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("타행 은행 목록: 당행 없이 5개를 은행코드 문자열 오름차순으로 반환한다")
    void getInterbankBanks_returnsFiveBanks() throws Exception {
        mockMvc.perform(get("/transfers/banks").with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].bankCode").value("004"))
                .andExpect(jsonPath("$.data[0].bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.data[4].bankCode").value("090"));
    }

    @Test
    @DisplayName("타행이체: 상대 은행이 처리하지 못하면 200 + ERROR(TRF0308)와 은행코드를 반환하고 출금은 되돌려진다")
    void executeInterbank_notProcessed_returnsErrorResult() throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        // 9999로 시작하는 계좌는 모의 서버가 9999로 답하고, 서버가 없으면 연결 실패다 — 둘 다 TRF0308이다.
        postInterbank(interbankRequestJson("004", "9999000000000", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("ERROR"))
                .andExpect(jsonPath("$.data.errorCode").value("TRF0308"))
                .andExpect(jsonPath("$.data.payeeBankCode").value("004"))
                .andExpect(jsonPath("$.data.payeeName").value(nullValue()))
                .andExpect(jsonPath("$.data.transactionNumber").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = 101", Long.class))
                .isEqualTo(100000L);
    }

    @Test
    @DisplayName("타행이체: 16자리를 넘는 계좌번호는 400 + TRF0007")
    void executeInterbank_invalidAccountNumber_returnsBadRequest() throws Exception {
        postInterbank(interbankRequestJson("004", "12345678901234567", "홍길동"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0007"));
    }

    @Test
    @DisplayName("타행이체: 전문 문자셋에 없는 문자(이모지)는 400 + TRF0008")
    void executeInterbank_emojiMemo_returnsBadRequest() throws Exception {
        postInterbank(interbankRequestJson("004", "1234567890123", "홍길동😀"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0008"));
    }

    @Test
    @DisplayName("타행이체: 목록에 없는 은행코드(당행 088 포함)는 400 + TRF0006")
    void executeInterbank_unsupportedBank_returnsBadRequest() throws Exception {
        postInterbank(interbankRequestJson("088", "1234567890123", "홍길동"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0006"));
    }

    @Test
    @DisplayName("타행 수취인 조회: 상대 은행이 확인하지 못하면(9999 또는 연결 실패) 409 + TRF0309")
    void inquireInterbankPayee_unverified_returnsConflict() throws Exception {
        // 9999로 시작하는 계좌는 모의 서버가 9999로 답하고, 서버가 없으면 연결 실패다 — 둘 다 TRF0309다.
        mockMvc.perform(get("/transfers/interbank/payee")
                        .with(authentication(authenticationOf(1L)))
                        .param("bankCode", "004")
                        .param("accountNumber", "9999000000000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRF0309"));
    }

    @Test
    @DisplayName("타행 수취인 조회: 계좌번호 형식이 틀리면 400 + TRF0007, 목록에 없는 은행이면 400 + TRF0006")
    void inquireInterbankPayee_invalidInput_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/transfers/interbank/payee")
                        .with(authentication(authenticationOf(1L)))
                        .param("bankCode", "004")
                        .param("accountNumber", "12345678901234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0007"));
        mockMvc.perform(get("/transfers/interbank/payee")
                        .with(authentication(authenticationOf(1L)))
                        .param("bankCode", "088")
                        .param("accountNumber", "1234567890123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0006"));
    }

    @Test
    @DisplayName("API 문서에 타행 수취인 조회가 operationId와 payeeName 응답으로 노출된다")
    void apiDocs_exposesInterbankPayeeInquiry() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/transfers/interbank/payee']['get']['operationId']")
                        .value("getInterbankTransferPayee"))
                .andExpect(jsonPath("$['components']['schemas']['InterbankPayeeResponse']['properties']['payeeName']")
                        .exists());
    }
}
