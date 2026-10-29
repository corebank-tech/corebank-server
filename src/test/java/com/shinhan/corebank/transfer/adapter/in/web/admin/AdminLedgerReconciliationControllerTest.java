package com.shinhan.corebank.transfer.adapter.in.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerReconciliationMismatchPersistenceAdapter;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("관리자 대사 불일치 목록 API")
class AdminLedgerReconciliationControllerTest extends IntegrationTestSupport {

    private static final long ADMIN_ID = 944930001L;
    private static final long NON_ADMIN_ID = 944930002L;
    private static final String URL = "/admin/ledger-reconciliation/mismatches";

    // 시드·다른 테스트와 겹치지 않는 전용 대역
    private static final Long ACCOUNT_A = 8_814_001L;
    private static final Long ACCOUNT_B = 8_814_002L;
    private static final LocalDate EARLIER_DATE = LocalDate.of(2099, 2, 3);
    private static final LocalDate LATEST_DATE = LocalDate.of(2099, 2, 4);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LedgerReconciliationMismatchPersistenceAdapter mismatchAdapter;

    @Autowired
    EntityManager entityManager;

    // 새 인가는 customer.role 과 admin_permission 행을 읽는다 — 허용 목록(#448) 자리를 이것이 대신한다.
    @BeforeEach
    void grantAdminPermission() {
        AdminAccountFixture.createAdmin(entityManager, ADMIN_ID, AdminPermission.AUDIT_READ);
    }

    @Test
    @DisplayName("영업일을 주면 그날 불일치를 §6-5 페이지 형식으로, 시각은 +09:00으로 내려준다")
    void returnsMismatchesOfRequestedDate() throws Exception {
        saveSample();

        mockMvc.perform(get(URL).param("businessDate", "2099-02-03").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].businessDate").value("2099-02-03"))
                .andExpect(jsonPath("$.data.items[0].accountId").value(ACCOUNT_A))
                .andExpect(jsonPath("$.data.items[0].ledgerBalance").value(1_000))
                .andExpect(jsonPath("$.data.items[0].accountBalance").value(900))
                .andExpect(jsonPath("$.data.items[0].difference").value(100))
                .andExpect(jsonPath("$.data.items[0].detectedAt").value("2099-02-03T23:31:05+09:00"));
    }

    @Test
    @DisplayName("영업일을 빼면 가장 최근 영업일을 보여준다")
    void defaultsToLatestDate() throws Exception {
        saveSample();

        mockMvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].businessDate").value("2099-02-04"))
                .andExpect(jsonPath("$.data.items[0].accountId").value(ACCOUNT_B))
                .andExpect(jsonPath("$.data.items[0].difference").value(-50));
    }

    @Test
    @DisplayName("all=true면 그 영업일 전체를 계좌 ID 오름차순 한 페이지로 내려준다")
    void all_returnsWholeDateInOnePage() throws Exception {
        mismatchAdapter.saveIfAbsent(
                EARLIER_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L),
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                LocalDateTime.of(2099, 2, 3, 23, 31, 5));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(URL).param("businessDate", "2099-02-03")
                        .param("all", "true")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].accountId").value(ACCOUNT_A))
                .andExpect(jsonPath("$.data.items[1].accountId").value(ACCOUNT_B));
    }

    @Test
    @DisplayName("같은 영업일로 대사가 다시 돌아도 처음 저장한 목록과 시각을 내려준다(#749)")
    void rerunForSameDate_returnsFirstRun() throws Exception {
        mismatchAdapter.saveIfAbsent(
                EARLIER_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                LocalDateTime.of(2099, 2, 3, 23, 31, 5));
        entityManager.flush();
        entityManager.clear();
        // 멈춘 COB 재개 때의 재대사 — A만 남았다
        mismatchAdapter.saveIfAbsent(
                EARLIER_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 950L)),
                LocalDateTime.of(2099, 2, 4, 23, 31, 5));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(URL).param("businessDate", "2099-02-03").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].accountId").value(ACCOUNT_A))
                .andExpect(jsonPath("$.data.items[0].accountBalance").value(900))
                .andExpect(jsonPath("$.data.items[0].detectedAt").value("2099-02-03T23:31:05+09:00"))
                .andExpect(jsonPath("$.data.items[1].accountId").value(ACCOUNT_B));
    }

    @Test
    @DisplayName("기록이 없는 영업일은 items가 빈 배열이다")
    void emptyDate_returnsEmptyItems() throws Exception {
        mockMvc.perform(get(URL).param("businessDate", "2099-03-01").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("관리자가 아닌 고객은 403 CMN0102")
    void nonAdmin_forbidden() throws Exception {
        mockMvc.perform(get(URL).with(customer(NON_ADMIN_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400 CMN0001")
    void invalidDate_badRequest() throws Exception {
        mockMvc.perform(get(URL).param("businessDate", "2099-13-01").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("지원하지 않는 페이지 크기는 400 CMN0005")
    void invalidPageSize_badRequest() throws Exception {
        mockMvc.perform(get(URL).param("size", "7").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    private void saveSample() {
        mismatchAdapter.saveIfAbsent(
                EARLIER_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                LocalDateTime.of(2099, 2, 3, 23, 31, 5));
        mismatchAdapter.saveIfAbsent(
                LATEST_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                LocalDateTime.of(2099, 2, 4, 23, 31, 5));
        entityManager.flush();
        entityManager.clear();
    }

    // 인가는 로그인 때 세션에 실린 authority 만 본다 — 실제 로그인까지 잇는 검증은
    // AdminCustomerAccountOperationIntegrationTest 가 맡는다.
    private RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "ph38admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", AdminPermission.AUDIT_READ.name())));
    }

    private RequestPostProcessor customer(long customerId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "ph38admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
