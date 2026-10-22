package com.shinhan.corebank.info.adapter.in.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 정보계 조회 API (스텁)")
class AdminInfoControllerTest extends IntegrationTestSupport {

    // 시드·다른 관리자 테스트와 겹치지 않게 이슈 번호 대역을 쓴다.
    private static final long ADMIN_ID = 675_000_001L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void grantAdminPermission() {
        AdminAccountFixture.createAdmin(entityManager, ADMIN_ID, AdminPermission.GL_READ);
    }

    @Test
    @DisplayName("일별 수신 잔액 추이는 200 · 0000 으로 설계서 §5-2 필드명 그대로 내려준다")
    void returnsDepositBalancesInDesignShape() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances").with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").value("2026-10-28"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].baseDate").value("2026-10-01"))
                .andExpect(jsonPath("$.data.items[0].totalBalance").value(1_523_400_000_000L))
                .andExpect(jsonPath("$.data.items[1].baseDate").value("2026-10-02"));
    }

    @Test
    @DisplayName("기간 인자를 줘도 같은 모양으로 200 이다")
    void acceptsOptionalPeriod() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-28")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("상품별 가입·해지는 200 · 0000 으로 설계서 §5-3 필드명 그대로 내려준다")
    void returnsProductSubscriptionsInDesignShape() throws Exception {
        mockMvc.perform(get("/admin/info/product-subscriptions")
                        .param("month", "2026-10")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").value("2026-10-28"))
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.subscribedCount").value(120))
                .andExpect(jsonPath("$.data.closedCount").value(5))
                .andExpect(jsonPath("$.data.items[0].productId").value(3))
                .andExpect(jsonPath("$.data.items[0].productCode").value("DEP-12M"))
                .andExpect(jsonPath("$.data.items[0].productName").value("쏠편한 정기예금"))
                .andExpect(jsonPath("$.data.items[0].productGroup").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.items[0].subscribedCount").value(80))
                .andExpect(jsonPath("$.data.items[0].closedCount").value(3));
    }

    @Test
    @DisplayName("month 를 생략해도 200 이다")
    void productSubscriptionsWithoutMonth() throws Exception {
        mockMvc.perform(get("/admin/info/product-subscriptions").with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-10"));
    }

    @Test
    @DisplayName("응답에 고객 식별자·가명 키 필드가 없다(REQ-ADM-033)")
    void responsesHaveNoPersonalFields() throws Exception {
        for (String path : new String[] {"/admin/info/deposit-balances", "/admin/info/product-subscriptions"}) {
            mockMvc.perform(get(path).with(admin(AdminPermission.GL_READ)))
                    .andExpect(jsonPath("$.data.items[0].customerId").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].customerKey").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].accountKey").doesNotExist())
                    .andExpect(jsonPath("$.data.customerId").doesNotExist());
        }
    }

    @Test
    @DisplayName("GL_READ 가 아닌 권한의 관리자는 403 CMN0102 — 두 경로 모두")
    void rejectsAdminWithoutGlRead() throws Exception {
        for (String path : new String[] {"/admin/info/deposit-balances", "/admin/info/product-subscriptions"}) {
            mockMvc.perform(get(path).with(admin(AdminPermission.CUSTOMER_READ)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CMN0102"));
        }
    }

    @Test
    @DisplayName("관리자가 아닌 고객은 403 CMN0102")
    void rejectsNonAdminCustomer() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances").with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401 CMN0101")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    private RequestPostProcessor admin(AdminPermission permission) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "issue675admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", permission.name())));
    }

    private RequestPostProcessor customer() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(675_000_002L, "issue675user", "사용자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
