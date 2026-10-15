package com.shinhan.corebank.gl.adapter.in.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.gl.adapter.out.persistence.GlVoucherPersistenceAdapter;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// 허용 목록 값을 AdminCustomerControllerTest 와 같게 둬 Spring 컨텍스트를 공유한다.
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
        properties = "app.security.admin.bootstrap-customer-ids=" + AdminTrialBalanceControllerTest.ADMIN_ID)
@DisplayName("관리자 시산표 API")
class AdminTrialBalanceControllerTest extends IntegrationTestSupport {

    static final long ADMIN_ID = 944900001L;

    // 시드와 다른 GL 테스트와 겹치지 않는 거래일 대역.
    private static final LocalDate DAY = LocalDate.of(2031, 2, 10);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GlVoucherPersistenceAdapter voucherPersistenceAdapter;

    @Test
    @DisplayName("200 · 0000 으로 FE TrialBalanceResult 필드명 그대로 내려준다")
    void returnsTrialBalanceInFeContractShape() throws Exception {
        voucherPersistenceAdapter.save(Voucher.create(
                new VoucherNumber(DAY, GlTxType.TRANSFER, 1),
                "PH28-CONTROLLER-1",
                null,
                List.of(JournalEntry.debit("20100", 1_000L), JournalEntry.credit("20100", 1_000L))));

        mockMvc.perform(get("/admin/trial-balance")
                        .param("fromDate", "2031-02-01")
                        .param("toDate", "2031-02-28")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.fromDate").value("2031-02-01"))
                .andExpect(jsonPath("$.data.toDate").value("2031-02-28"))
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].accountCode").value("20100"))
                .andExpect(jsonPath("$.data.rows[0].accountName").value("예수금"))
                .andExpect(jsonPath("$.data.rows[0].accountClass").value("LIABILITY"))
                .andExpect(jsonPath("$.data.rows[0].normalBalance").value("CREDIT"))
                .andExpect(jsonPath("$.data.rows[0].debitTotal").value(1_000))
                .andExpect(jsonPath("$.data.rows[0].creditTotal").value(1_000))
                .andExpect(jsonPath("$.data.rows[0].entryCount").value(2))
                .andExpect(jsonPath("$.data.debitGrandTotal").value(1_000))
                .andExpect(jsonPath("$.data.creditGrandTotal").value(1_000))
                .andExpect(jsonPath("$.data.difference").value(0))
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.journalEntryCount").value(2));
    }

    @Test
    @DisplayName("기간 누락은 CMN0002, 역전은 CMN0003, 1년 초과는 CMN0004")
    void rejectsInvalidPeriod() throws Exception {
        mockMvc.perform(get("/admin/trial-balance")
                        .param("fromDate", "2031-02-01")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(get("/admin/trial-balance")
                        .param("fromDate", "2031-02-10")
                        .param("toDate", "2031-02-09")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0003"));
        mockMvc.perform(get("/admin/trial-balance")
                        .param("fromDate", "2030-02-01")
                        .param("toDate", "2031-02-02")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0004"));
    }

    @Test
    @DisplayName("허용 목록 밖 고객은 403 CMN0102")
    void rejectsCustomerOutsideAllowList() throws Exception {
        mockMvc.perform(get("/admin/trial-balance")
                        .param("fromDate", "2031-02-01")
                        .param("toDate", "2031-02-28")
                        .with(customer(944900999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    private RequestPostProcessor admin() {
        return customer(ADMIN_ID);
    }

    private RequestPostProcessor customer(long customerId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "ph28user", "사용자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
