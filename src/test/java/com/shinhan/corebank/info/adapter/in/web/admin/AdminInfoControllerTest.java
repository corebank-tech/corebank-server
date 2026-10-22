package com.shinhan.corebank.info.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import jakarta.persistence.EntityManager;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
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
@DisplayName("관리자 정보계 조회 API (적재 이력 없음 스텁)")
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
    @DisplayName("일별 수신 잔액 추이는 적재 이력이 없으면 200 · 0000 · asOf null · 빈 배열이다(REQ-ADM-032)")
    void depositBalancesWithoutLoadHistory() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances").with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").value(nullValue()))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("기간 인자를 줘도 같은 응답으로 200 이다")
    void acceptsOptionalPeriod() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-28")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOf").value(nullValue()))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("상품별 가입·해지는 적재 이력이 없으면 건수 null · 빈 배열이고 요청한 달을 그대로 돌려준다")
    void productSubscriptionsWithoutLoadHistory() throws Exception {
        mockMvc.perform(get("/admin/info/product-subscriptions")
                        .param("month", "2026-10")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.asOf").value(nullValue()))
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.subscribedCount").value(nullValue()))
                .andExpect(jsonPath("$.data.closedCount").value(nullValue()))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("month 를 생략하면 month 도 null 이다")
    void productSubscriptionsWithoutMonth() throws Exception {
        mockMvc.perform(get("/admin/info/product-subscriptions").with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(nullValue()));
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 400 CMN0003")
    void rejectsReversedPeriod() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2026-10-10")
                        .param("to", "2026-10-09")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0003"));
    }

    @Test
    @DisplayName("조회기간이 365일을 넘으면 400 CMN0004 이고 365일 정확히는 통과한다")
    void enforcesMaximumPeriod() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2025-10-01")
                        .param("to", "2026-10-02")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0004"));
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2025-10-01")
                        .param("to", "2026-10-01")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("from 이나 to 하나만 주면 기본값 규칙(#704)이 정해질 때까지 통과한다")
    void singleBoundIsAccepted() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("from", "2020-01-01")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/info/deposit-balances")
                        .param("to", "2026-10-01")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("날짜·달 형식이 틀리면 400 CMN0001")
    void rejectsMalformedParameters() throws Exception {
        mockMvc.perform(get("/admin/info/deposit-balances").param("from", "abc").with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(get("/admin/info/product-subscriptions")
                        .param("month", "2026-13")
                        .with(admin(AdminPermission.GL_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("응답 필드 이름은 설계서 §5-2 · §5-3 과 같고 고객 식별자·가명 키 필드가 없다(REQ-ADM-033)")
    void responseFieldNamesFollowDesign() {
        assertThat(names(DepositBalancesResponse.class)).containsExactly("asOf", "items");
        assertThat(names(DepositBalancesResponse.DepositBalanceItem.class)).containsExactly("baseDate", "totalBalance");
        assertThat(names(ProductSubscriptionsResponse.class))
                .containsExactly("asOf", "month", "subscribedCount", "closedCount", "items");
        assertThat(names(ProductSubscriptionsResponse.ProductSubscriptionItem.class))
                .containsExactly(
                        "productId", "productCode", "productName", "productGroup", "subscribedCount", "closedCount");
        List<String> all = List.of(
                        DepositBalancesResponse.class,
                        DepositBalancesResponse.DepositBalanceItem.class,
                        ProductSubscriptionsResponse.class,
                        ProductSubscriptionsResponse.ProductSubscriptionItem.class)
                .stream()
                .flatMap(type -> names(type).stream())
                .toList();
        assertThat(all)
                .noneMatch(name -> name.toLowerCase().contains("customer")
                        || name.toLowerCase().contains("key"));
    }

    @Test
    @DisplayName("Swagger 문서에 두 경로가 operationId 와 함께 노출된다")
    void swaggerDocumentExposesBothPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/admin/info/deposit-balances'].get.operationId")
                        .value("getAdminInfoDepositBalances"))
                .andExpect(jsonPath("$.paths['/admin/info/product-subscriptions'].get.operationId")
                        .value("getAdminInfoProductSubscriptions"))
                // 두 응답의 항목 스키마가 같은 이름으로 겹치면 FE 코드 생성이 한쪽 모양을 잃는다
                .andExpect(jsonPath("$.components.schemas.DepositBalanceItem.properties.baseDate")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.DepositBalanceItem.properties.totalBalance")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ProductSubscriptionItem.properties.productCode")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.Item").doesNotExist());
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

    private static List<String> names(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
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
