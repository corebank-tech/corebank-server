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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
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

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("정상 이체 요청은 200 + ApiResponse 봉투로 SUCCESS 결과를 반환한다")
    void execute_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(post("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "dummy-auth-token")
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
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        mockMvc.perform(post("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Account-Password-Auth-Token", "dummy-auth-token")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(101L, "110222222222", 30000L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하는 계좌번호로 예금주를 조회하면 200 + 예금주명을 반환한다")
    void inquirePayee_success() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

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

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private String transferRequestJson(Long withdrawalAccountId, String depositAccountNumber, long amount) throws Exception {
        TransferRequest request = new TransferRequest(withdrawalAccountId, depositAccountNumber, amount, "출금메모", "입금메모");
        return OBJECT_MAPPER.writeValueAsString(request);
    }
}
