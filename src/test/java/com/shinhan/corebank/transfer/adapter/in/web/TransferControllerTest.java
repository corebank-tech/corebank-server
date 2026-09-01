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
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101");
        jdbcTemplate.update("DELETE FROM transfer WHERE transaction_number IN "
                + "('20260810IT0000000010','20260810IT0000000011','20260810IT0000000012')");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
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
}
