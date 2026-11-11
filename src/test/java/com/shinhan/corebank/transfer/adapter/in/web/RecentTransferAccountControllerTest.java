package com.shinhan.corebank.transfer.adapter.in.web;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@DisplayName("최근 이체계좌 목록 API 통합 테스트")
class RecentTransferAccountControllerTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 계좌 ID·계좌번호·거래번호 대역(1972xx).
    private static final long PAYEE_ACTIVE = 197_201L;
    private static final long PAYEE_SUSPENDED = 197_202L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        TransferTestFixtures.seedAnotherCustomer(entityManager);
        insertAccount(PAYEE_ACTIVE, "110197200001", "ACTIVE");
        insertAccount(PAYEE_SUSPENDED, "110197200002", "SUSPENDED");
    }

    @Test
    @DisplayName("로그인 고객이 최근 보낸 계좌를 최신순으로 주고, 예금주명은 원문·시각은 +09:00·정지 계좌는 transferable=false다")
    void list_returnsRecentAccountsOfLoggedInCustomer() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertTransfer(1, PAYEE_SUSPENDED, "110197200002", now.minusDays(2));
        insertTransfer(2, PAYEE_ACTIVE, "110197200001", now.minusDays(1).withNano(0));

        mockMvc.perform(get("/transfers/recent-accounts").with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].depositAccountNumber").value("110197200001"))
                .andExpect(jsonPath("$.data[0].payeeName").value("테스터2"))
                .andExpect(jsonPath("$.data[0].lastTransferredAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data[0].transferable").value(true))
                .andExpect(jsonPath("$.data[1].depositAccountNumber").value("110197200002"))
                .andExpect(jsonPath("$.data[1].transferable").value(false));
    }

    @Test
    @DisplayName("다른 고객의 이체는 보이지 않고 빈 배열이다")
    void list_withoutOwnTransfers_returnsEmptyArray() throws Exception {
        insertTransfer(1, PAYEE_ACTIVE, "110197200001", LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/transfers/recent-accounts").with(authentication(authenticationOf(2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void insertAccount(long accountId, String accountNumber, String status) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status,
                                             password_hash, withdrawal_registered, withdrawal_registered_at, opened_date,
                                             created_at, updated_at)
                        VALUES (:id, :number, 2, NULL, 'DEMAND_DEPOSIT', 100000, :status,
                                '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01',
                                NOW(6), NOW(6))
                        """)
                .setParameter("id", accountId)
                .setParameter("number", accountNumber)
                .setParameter("status", status)
                .executeUpdate();
    }

    private void insertTransfer(int serial, long depositAccountId, String depositAccountNumber, LocalDateTime at) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id,
                                              deposit_account_number, payee_name, amount, fee, transfer_type, channel,
                                              status, transferred_at, created_at)
                        VALUES (:txNo, 101, :to, :toNumber, '옛이름', 10000, 0, 'IMMEDIATE', 'WB', 'SUCCESS', :at, NOW(6))
                        """)
                .setParameter("txNo", String.format("20310801IT197%07d", serial))
                .setParameter("to", depositAccountId)
                .setParameter("toNumber", depositAccountNumber)
                .setParameter("at", at)
                .executeUpdate();
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
