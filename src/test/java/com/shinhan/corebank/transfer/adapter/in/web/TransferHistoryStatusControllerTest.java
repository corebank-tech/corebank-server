package com.shinhan.corebank.transfer.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DisplayName("이체결과조회 상태 필터 API 테스트")
class TransferHistoryStatusControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @BeforeEach
    void seed() {
        cleanUp();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager
                    .createNativeQuery(
                            """
                            INSERT INTO transfer_hold
                                (customer_id, withdrawal_account_id, deposit_account_number, amount, channel,
                                 policy_code, release_mode, status, authenticated_at, held_at)
                            VALUES (1, 101, '110222222222', 500000, 'WB', 'A', 'ON_APPROVAL', 'HELD', NOW(6), NOW(6))
                            """)
                    .executeUpdate();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id = 1");
    }

    @Test
    @DisplayName("HELD 필터는 보류 거래만 반환한다")
    void searchHistory_heldStatus_returnsHeldHistory() throws Exception {
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("status", "HELD")
                        .param("fromDate", today.toString())
                        .param("toDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].historyId").value(org.hamcrest.Matchers.startsWith("HOLD:")))
                .andExpect(jsonPath("$.data.items[0].status").value("HELD"))
                .andExpect(jsonPath("$.data.items[0].transactionNumber").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].payeeName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("ALL 필터는 상태 조건 없이 전체를 반환한다")
    void searchHistory_allStatus_returnsAllHistory() throws Exception {
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("status", "ALL")
                        .param("fromDate", today.toString())
                        .param("toDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("허용하지 않는 상태는 CMN0001을 반환한다")
    void searchHistory_invalidStatus_returnsInvalidInput() throws Exception {
        mockMvc.perform(get("/transfers")
                        .with(authentication(authenticationOf(1L)))
                        .param("withdrawalAccountId", "101")
                        .param("status", "PROCESSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
