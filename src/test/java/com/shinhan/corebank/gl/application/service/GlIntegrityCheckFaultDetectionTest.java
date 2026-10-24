package com.shinhan.corebank.gl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.gl.adapter.out.persistence.GlVoucherPersistenceAdapter;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.in.GlIntegrityCheckUseCase;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQuery;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQueryUseCase;
import com.shinhan.corebank.gl.domain.GlIntegrityReport;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PH-28b 탐지율 행렬. 결함을 GL 층과 원장 층에 하나씩 주입하고 탐지 수단 4개가 각각 잡는지 본다.
 *
 * <p>탐지 수단은 P4 원장-잔액 전수 대사({@code reconcileAll}), 전표 차대변 점검, 시산표 차대변 일치, 원장↔전표 대사다.
 * 원장↔전표 대사는 불일치 목록과 고아 전표 수를 합쳐 판정한다.
 * 앞의 셋이 기존 수단이고 마지막이 PH-28b 가 더한 것이다. 결과는 DB가 막음 / 탐지 / 미탐지 셋 중 하나다.
 *
 * <p>정상 거래 하나를 함께 두어 결함이 아닌 거래를 잡지 않는지(오탐)도 본다. 이 표가 탐지율의 정본이고
 * docs/phase2/ph28b_fault_injection_results.md 가 옮겨 싣는다.
 */
@DisplayName("GL 정합성 점검 — 결함 위치 × 탐지 수단 행렬")
class GlIntegrityCheckFaultDetectionTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 고객·계좌·원장 ID와 거래일 대역(2031-05).
    private static final long ID_FROM = 928_200_001L;
    private static final long ID_TO = 928_200_999L;
    private static final long CUSTOMER_ID = 928_200_001L;
    private static final long ACCOUNT_FROM = 928_200_001L;
    private static final long ACCOUNT_TO = 928_200_002L;
    private static final LocalDate DAY = LocalDate.of(2031, 5, 12);
    private static final long OPENING = 100_000L;
    private static final long AMOUNT = 10_000L;

    private static final String FAULTY_TX = "20310512WB0000000001";
    private static final String CLEAN_TX = "20310512WB0000000002";
    private static final String FAULTY_VOUCHER = new VoucherNumber(DAY, GlTxType.TRANSFER, 1).value();
    private static final String COPIED_VOUCHER = new VoucherNumber(DAY, GlTxType.TRANSFER, 9).value();

    @Autowired
    private GlIntegrityCheckUseCase integrityCheck;

    @Autowired
    private TrialBalanceQueryUseCase trialBalance;

    @Autowired
    private LedgerFullReconciliationUseCase ledgerReconciliation;

    @Autowired
    private GlVoucherPersistenceAdapter voucherPersistenceAdapter;

    @Autowired
    private JdbcTemplate jdbc;

    private long nextLedgerId = ID_FROM;

    /** 탐지 수단 4개의 판정. 순서는 P4 대사, 전표 차대변, 시산표, 원장↔전표. */
    record Detection(boolean ledgerBalance, boolean voucherBalance, boolean trialBalance, boolean ledgerVoucher) {

        static final Detection DB_BLOCKED = null;

        boolean byExistingControls() {
            return ledgerBalance || voucherBalance || trialBalance;
        }

        boolean byAnyControl() {
            return byExistingControls() || ledgerVoucher;
        }
    }

    private static Detection detected(boolean p4, boolean voucher, boolean trial, boolean ledgerVoucher) {
        return new Detection(p4, voucher, trial, ledgerVoucher);
    }

    enum Fault {
        // ── GL 층 (gl_voucher · gl_journal_entry)
        GL_AMOUNT_TAMPERING("GL 금액변조 — 대변 분개 금액 +1", detected(false, true, true, true)),
        GL_ONE_SIDED_POSTING("GL 편측기표 — 대변 분개 삭제", detected(false, true, true, true)),
        GL_MISSING_JOURNAL("GL 분개누락 — 전표와 분개 삭제", detected(false, false, false, true)),
        GL_DUPLICATE_SAME_KEY("GL 중복기표 — 같은 (유형, 참조 키)로 전표 복제", Detection.DB_BLOCKED),
        GL_DUPLICATE_OTHER_KEY("GL 중복기표 — 다른 참조 키로 전표 복제", detected(false, false, false, true)),
        GL_DUPLICATE_LINES("GL 중복기표 — 같은 전표에 분개 쌍 추가", detected(false, false, false, true)),
        // ── 원장 층 (ledger_entry)
        LEDGER_AMOUNT_TAMPERING("원장 금액변조 — 입금 행 금액 +1", detected(true, false, false, false)),
        LEDGER_ONE_SIDED_POSTING("원장 편측기표 — 입금 행 삭제", detected(true, false, false, false)),
        LEDGER_MISSING_ENTRY("원장 누락 — 출금·입금 행 삭제", detected(true, false, false, true)),
        LEDGER_DUPLICATE_SAME_TX("원장 중복기표 — 같은 거래번호로 2행 추가", detected(true, false, false, false)),
        LEDGER_DUPLICATE_NEW_TX("원장 중복기표 — 새 거래번호로 2행 추가", detected(true, false, false, true));

        final String label;
        final Detection expected;

        Fault(String label, Detection expected) {
            this.label = label;
            this.expected = expected;
        }
    }

    @BeforeEach
    void seedBalancedBooks() {
        jdbc.update(
                """
                INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number,
                                      joined_at, created_at, updated_at)
                VALUES (?, 'ph28bmatrix', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터',
                        '1990-01-01', 'ph28b@test.com', '01092820001', NOW(6), NOW(6), NOW(6))
                """,
                CUSTOMER_ID);
        insertAccount(ACCOUNT_FROM, "928200000001", OPENING - 2 * AMOUNT);
        insertAccount(ACCOUNT_TO, "928200000002", OPENING + 2 * AMOUNT);
        insertLedgerRow(ACCOUNT_FROM, "20310512BT0000000901", "OPENING", "DEPOSIT", OPENING);
        insertLedgerRow(ACCOUNT_TO, "20310512BT0000000902", "OPENING", "DEPOSIT", OPENING);
        postTransfer(FAULTY_TX, 1);
        postTransfer(CLEAN_TX, 2);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?", ID_FROM, ID_TO);
        jdbc.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", DAY);
        jdbc.update("DELETE FROM gl_voucher WHERE trade_date = ?", DAY);
        jdbc.update("DELETE FROM account WHERE account_id BETWEEN ? AND ?", ID_FROM, ID_TO);
        jdbc.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("결함이 없으면 탐지 수단 4개 모두 아무것도 잡지 않는다")
    void cleanBooks() {
        assertThat(detect()).isEqualTo(detected(false, false, false, false));

        GlIntegrityReport report = integrityCheck.check(DAY);
        assertThat(report.checkedTransactionCount()).isEqualTo(2);
        assertThat(report.excludedTransactionCounts()).isEmpty();
        assertThat(report.orphanVoucherCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Fault.class)
    @DisplayName("결함마다 기대한 탐지 수단에서만 잡히거나 DB가 막는다")
    void matrix(Fault fault) {
        if (fault.expected == Detection.DB_BLOCKED) {
            assertThatThrownBy(() -> inject(fault)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(detect()).isEqualTo(detected(false, false, false, false));
            return;
        }
        inject(fault);

        assertThat(detect()).as(fault.label).isEqualTo(fault.expected);
        assertNoFalsePositiveOnCleanTransaction();
    }

    @Test
    @DisplayName("대사 대상이 아닌 원장 유형은 대사하지 않고 유형별 건수로 남긴다")
    void countsExcludedTypes() {
        insertLedgerRow(ACCOUNT_FROM, "20310512WB0000000003", "INTERBANK_TRANSFER", "WITHDRAWAL", 5_000L);
        insertLedgerRow(ACCOUNT_TO, "20310512BT0000000004", "INTEREST", "DEPOSIT", 70L);

        GlIntegrityReport report = integrityCheck.check(DAY);

        assertThat(report.ledgerVoucherMismatchCount()).isZero();
        assertThat(report.checkedTransactionCount()).isEqualTo(2);
        assertThat(report.excludedTransactionCounts()).isEqualTo(Map.of("INTERBANK_TRANSFER", 1L, "INTEREST", 1L));
    }

    private Detection detect() {
        List<LedgerReconciliationMismatch> ledgerBalance = ledgerReconciliation.reconcileAll().stream()
                .filter(m -> m.accountId() >= ID_FROM && m.accountId() <= ID_TO)
                .toList();
        GlIntegrityReport report = integrityCheck.check(DAY);
        boolean trialBalanceOff =
                !trialBalance.query(new TrialBalanceQuery(DAY, DAY)).balanced();
        return detected(
                !ledgerBalance.isEmpty(),
                report.unbalancedVoucherCount() > 0,
                trialBalanceOff,
                report.ledgerVoucherMismatchCount() > 0 || report.orphanVoucherCount() > 0);
    }

    // 결함을 잡은 수단이 정상 거래까지 결함으로 내지 않는다.
    private void assertNoFalsePositiveOnCleanTransaction() {
        GlIntegrityReport report = integrityCheck.check(DAY);
        assertThat(report.ledgerVoucherMismatches())
                .noneMatch(m -> m.transactionNumber().equals(CLEAN_TX));
        assertThat(report.unbalancedVouchers())
                .noneMatch(v -> v.voucherNo().equals(new VoucherNumber(DAY, GlTxType.TRANSFER, 2).value()));
    }

    private void inject(Fault fault) {
        Map<Fault, Consumer<JdbcTemplate>> injections = Map.ofEntries(
                Map.entry(
                        Fault.GL_AMOUNT_TAMPERING,
                        db -> db.update(
                                "UPDATE gl_journal_entry SET amount = amount + 1 WHERE voucher_no = ? AND dr_cr = 'CREDIT'",
                                FAULTY_VOUCHER)),
                Map.entry(
                        Fault.GL_ONE_SIDED_POSTING,
                        db -> db.update(
                                "DELETE FROM gl_journal_entry WHERE voucher_no = ? AND dr_cr = 'CREDIT'",
                                FAULTY_VOUCHER)),
                Map.entry(Fault.GL_MISSING_JOURNAL, db -> {
                    db.update("DELETE FROM gl_journal_entry WHERE voucher_no = ?", FAULTY_VOUCHER);
                    db.update("DELETE FROM gl_voucher WHERE voucher_no = ?", FAULTY_VOUCHER);
                }),
                Map.entry(Fault.GL_DUPLICATE_SAME_KEY, db -> copyVoucher(db, FAULTY_TX)),
                Map.entry(Fault.GL_DUPLICATE_OTHER_KEY, db -> copyVoucher(db, FAULTY_TX + "-DUP")),
                Map.entry(
                        Fault.GL_DUPLICATE_LINES,
                        db -> db.update(
                                """
                                INSERT INTO gl_journal_entry (voucher_no, line_no, account_code, dr_cr, amount, trade_date)
                                SELECT voucher_no, line_no + 2, account_code, dr_cr, amount, trade_date
                                FROM gl_journal_entry WHERE voucher_no = ?
                                """,
                                FAULTY_VOUCHER)),
                Map.entry(
                        Fault.LEDGER_AMOUNT_TAMPERING,
                        db -> db.update(
                                "UPDATE ledger_entry SET amount = amount + 1"
                                        + " WHERE transaction_number = ? AND direction = 'DEPOSIT'",
                                FAULTY_TX)),
                Map.entry(
                        Fault.LEDGER_ONE_SIDED_POSTING,
                        db -> db.update(
                                "DELETE FROM ledger_entry WHERE transaction_number = ? AND direction = 'DEPOSIT'",
                                FAULTY_TX)),
                Map.entry(
                        Fault.LEDGER_MISSING_ENTRY,
                        db -> db.update("DELETE FROM ledger_entry WHERE transaction_number = ?", FAULTY_TX)),
                Map.entry(Fault.LEDGER_DUPLICATE_SAME_TX, db -> insertTransferRows(FAULTY_TX)),
                Map.entry(Fault.LEDGER_DUPLICATE_NEW_TX, db -> insertTransferRows("20310512WB0000000009")));
        injections.get(fault).accept(jdbc);
    }

    // 다른 전표번호로 전표와 분개를 복제한다. 참조 키가 같으면 uk_gl_voucher_tx_type_reference_key 가 거부한다.
    private static void copyVoucher(JdbcTemplate db, String referenceKey) {
        db.update(
                """
                INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, reference_key, description)
                SELECT ?, trade_date, tx_type, ?, description FROM gl_voucher WHERE voucher_no = ?
                """,
                COPIED_VOUCHER,
                referenceKey,
                FAULTY_VOUCHER);
        db.update(
                """
                INSERT INTO gl_journal_entry (voucher_no, line_no, account_code, dr_cr, amount, trade_date)
                SELECT ?, line_no, account_code, dr_cr, amount, trade_date
                FROM gl_journal_entry WHERE voucher_no = ?
                """,
                COPIED_VOUCHER,
                FAULTY_VOUCHER);
    }

    // gl_journal_patterns.md §3-2 패턴으로 원장 2행과 전표 1건(차 20100 / 대 20100)을 남긴다.
    private void postTransfer(String transactionNumber, int voucherSequence) {
        insertTransferRows(transactionNumber);
        voucherPersistenceAdapter.save(Voucher.create(
                new VoucherNumber(DAY, GlTxType.TRANSFER, voucherSequence),
                transactionNumber,
                null,
                List.of(JournalEntry.debit("20100", AMOUNT), JournalEntry.credit("20100", AMOUNT))));
    }

    private void insertTransferRows(String transactionNumber) {
        insertLedgerRow(ACCOUNT_FROM, transactionNumber, "IMMEDIATE_TRANSFER", "WITHDRAWAL", AMOUNT);
        insertLedgerRow(ACCOUNT_TO, transactionNumber, "IMMEDIATE_TRANSFER", "DEPOSIT", AMOUNT);
    }

    private void insertAccount(long accountId, String accountNumber, long balance) {
        jdbc.update(
                """
                INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status,
                                     password_hash, withdrawal_registered, withdrawal_registered_at, opened_date,
                                     created_at, updated_at)
                VALUES (?, ?, ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE',
                        '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2031-05-01',
                        NOW(6), NOW(6))
                """,
                accountId,
                accountNumber,
                CUSTOMER_ID,
                balance);
    }

    private void insertLedgerRow(long accountId, String transactionNumber, String type, String direction, long amount) {
        jdbc.update(
                """
                INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount,
                                          balance_after, transaction_type, transaction_content, channel, reversed,
                                          reversal_id, occurred_at, trade_date)
                VALUES (?, ?, NULL, ?, ?, ?, 0, ?, 'PH28B', ?, FALSE, NULL, ?, ?)
                """,
                nextLedgerId++,
                accountId,
                transactionNumber,
                direction,
                amount,
                type,
                transactionNumber.substring(8, 10),
                DAY.atTime(10, 0),
                DAY);
    }
}
