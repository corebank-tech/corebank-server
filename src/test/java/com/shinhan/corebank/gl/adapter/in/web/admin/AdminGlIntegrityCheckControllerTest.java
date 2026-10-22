package com.shinhan.corebank.gl.adapter.in.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.gl.adapter.out.persistence.GlVoucherPersistenceAdapter;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 GL 정합성 점검 API")
class AdminGlIntegrityCheckControllerTest extends IntegrationTestSupport {

    private static final long ADMIN_ID = 944920002L;
    private static final long AUDITOR_ID = 944920003L;

    // 시드와 다른 테스트와 겹치지 않는 거래일·원장 ID 대역(2031-06).
    private static final LocalDate DAY = LocalDate.of(2031, 6, 10);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GlVoucherPersistenceAdapter voucherPersistenceAdapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    // 인가는 customer.role 과 admin_permission 행을 읽는다(admin_auth.md §6).
    @BeforeEach
    void grantAdminPermission() {
        AdminAccountFixture.createAdmin(entityManager, ADMIN_ID, AdminPermission.GL_READ);
        AdminAccountFixture.createAdmin(entityManager, AUDITOR_ID, AdminPermission.AUDIT_READ);
    }

    @Test
    @DisplayName("200 · 0000 으로 두 점검 결과와 건수를 내려준다")
    void returnsReport() throws Exception {
        // 차변만 있는 전표처럼 보이게 할 수는 없으니(도메인이 막음) 대변 줄을 지워 편측기표를 만든다.
        String voucherNo = new VoucherNumber(DAY, GlTxType.TRANSFER, 1).value();
        voucherPersistenceAdapter.save(Voucher.create(
                new VoucherNumber(DAY, GlTxType.TRANSFER, 1),
                "20310610WB0000000001",
                null,
                List.of(JournalEntry.debit("20100", 1_000L), JournalEntry.credit("20100", 1_000L))));
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE voucher_no = ? AND dr_cr = 'CREDIT'", voucherNo);
        insertLedgerRow(928_300_001L, "20310610WB0000000001", "IMMEDIATE_TRANSFER", 1_000L);
        insertLedgerRow(928_300_002L, "20310610WB0000000002", "IMMEDIATE_TRANSFER", 2_000L);
        insertLedgerRow(928_300_003L, "20310610WB0000000003", "INTERBANK_TRANSFER", 3_000L);

        mockMvc.perform(get("/admin/gl/integrity-check")
                        .param("tradeDate", "2031-06-10")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.tradeDate").value("2031-06-10"))
                .andExpect(jsonPath("$.data.checkedTransactionCount").value(2))
                .andExpect(jsonPath("$.data.excludedTransactionCounts.INTERBANK_TRANSFER")
                        .value(1))
                .andExpect(jsonPath("$.data.unbalancedVouchers.length()").value(1))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].voucherNo").value(voucherNo))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].txType").value("TRANSFER"))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].referenceKey").value("20310610WB0000000001"))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].debitTotal").value(1_000))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].creditTotal").value(0))
                .andExpect(jsonPath("$.data.unbalancedVouchers[0].entryCount").value(1))
                .andExpect(jsonPath("$.data.unbalancedVoucherCount").value(1))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches.length()").value(2))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[0].transactionNumber")
                        .value("20310610WB0000000001"))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[0].kind").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[0].ledgerAmount")
                        .value(1_000))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[0].voucherDebitTotal")
                        .value(1_000))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[0].voucherCreditTotal")
                        .value(0))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[1].transactionNumber")
                        .value("20310610WB0000000002"))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[1].ledgerTransactionType")
                        .value("IMMEDIATE_TRANSFER"))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatches[1].kind").value("MISSING_VOUCHER"))
                .andExpect(jsonPath("$.data.ledgerVoucherMismatchCount").value(2))
                .andExpect(jsonPath("$.data.orphanVoucherCount").value(0))
                // 2031-06-10 은 테스트 환경의 현재 영업일보다 뒤라 마감 전 거래일이다
                .andExpect(jsonPath("$.data.orphanVoucherCountFinal").value(false))
                .andExpect(jsonPath("$.data.clean").value(false));
    }

    @Test
    @DisplayName("tradeDate 누락은 CMN0002, 형식 오류는 CMN0001")
    void rejectsInvalidTradeDate() throws Exception {
        mockMvc.perform(get("/admin/gl/integrity-check").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(get("/admin/gl/integrity-check")
                        .param("tradeDate", "2031-13-01")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("GL_READ 가 없는 관리자와 관리자가 아닌 고객은 403 CMN0102")
    void rejectsWithoutGlRead() throws Exception {
        mockMvc.perform(get("/admin/gl/integrity-check")
                        .param("tradeDate", "2031-06-10")
                        .with(admin(AUDITOR_ID, AdminPermission.AUDIT_READ)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
        mockMvc.perform(get("/admin/gl/integrity-check")
                        .param("tradeDate", "2031-06-10")
                        .with(customer(944900999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    private void insertLedgerRow(long ledgerEntryId, String transactionNumber, String type, long amount) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount,
                                          balance_after, transaction_type, transaction_content, channel, reversed,
                                          reversal_id, occurred_at, trade_date)
                VALUES (?, 928300001, NULL, ?, 'WITHDRAWAL', ?, 0, ?, 'PH28B', 'WB', FALSE, NULL, ?, ?)
                """,
                ledgerEntryId,
                transactionNumber,
                amount,
                type,
                DAY.atTime(10, 0),
                DAY);
    }

    // 인가는 로그인 때 세션에 실린 authority 만 본다. 실제 로그인까지 잇는 검증은
    // AdminCustomerAccountOperationIntegrationTest 가 맡는다.
    private RequestPostProcessor admin() {
        return admin(ADMIN_ID, AdminPermission.GL_READ);
    }

    private RequestPostProcessor admin(long customerId, AdminPermission permission) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "ph28badmin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", permission.name())));
    }

    private RequestPostProcessor customer(long customerId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "ph28buser", "사용자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
