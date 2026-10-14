package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import com.shinhan.corebank.gl.domain.GlTxType;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("시산표 집계(TrialBalanceQueryAdapter) 통합 테스트")
class TrialBalanceQueryAdapterTest extends IntegrationTestSupport {

    // 시드(PH-60 · PH-60b)와 다른 GL 테스트(2099-01)와 겹치지 않는 거래일 대역만 쓴다.
    private static final LocalDate BAND_FROM = LocalDate.of(2031, 1, 1);
    private static final LocalDate BAND_TO = LocalDate.of(2031, 1, 31);

    @Autowired
    private TrialBalanceQueryAdapter adapter;

    @Autowired
    private GlVoucherPersistenceAdapter voucherPersistenceAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date BETWEEN ? AND ?", BAND_FROM, BAND_TO);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date BETWEEN ? AND ?", BAND_FROM, BAND_TO);
    }

    private void saveVoucher(LocalDate tradeDate, int sequence, JournalEntry... entries) {
        voucherPersistenceAdapter.save(
                Voucher.create(new VoucherNumber(tradeDate, GlTxType.TRANSFER, sequence), null, List.of(entries)));
    }

    @Test
    @DisplayName("기간 양 끝 날짜의 분개는 들어가고 기간 밖 분개는 빠진다")
    void includesBothEndsAndExcludesOutside() {
        saveVoucher(
                LocalDate.of(2031, 1, 10), 1, JournalEntry.debit("20100", 100L), JournalEntry.credit("20100", 100L));
        saveVoucher(
                LocalDate.of(2031, 1, 20), 1, JournalEntry.debit("20100", 200L), JournalEntry.credit("20100", 200L));
        saveVoucher(
                LocalDate.of(2031, 1, 9), 1, JournalEntry.debit("20100", 1_000L), JournalEntry.credit("20100", 1_000L));
        saveVoucher(
                LocalDate.of(2031, 1, 21),
                1,
                JournalEntry.debit("20100", 1_000L),
                JournalEntry.credit("20100", 1_000L));

        List<TrialBalanceLine> rows = adapter.sumByAccount(LocalDate.of(2031, 1, 10), LocalDate.of(2031, 1, 20));

        assertThat(rows)
                .extracting(
                        TrialBalanceLine::accountCode,
                        TrialBalanceLine::debitTotal,
                        TrialBalanceLine::creditTotal,
                        TrialBalanceLine::entryCount)
                .containsExactly(tuple("20100", 300L, 300L, 4L));
    }

    @Test
    @DisplayName("계정별로 차변·대변을 따로 합산하고 계정과목 정보를 붙여 계정코드 오름차순으로 낸다")
    void sumsPerAccountInCodeOrder() {
        LocalDate day = LocalDate.of(2031, 1, 15);
        saveVoucher(day, 1, JournalEntry.debit("50100", 700L), JournalEntry.credit("20100", 700L));
        saveVoucher(day, 2, JournalEntry.debit("10100", 300L), JournalEntry.credit("20100", 300L));
        saveVoucher(day, 3, JournalEntry.debit("20100", 50L), JournalEntry.credit("10100", 50L));

        List<TrialBalanceLine> rows = adapter.sumByAccount(BAND_FROM, BAND_TO);

        assertThat(rows)
                .extracting(
                        TrialBalanceLine::accountCode,
                        TrialBalanceLine::accountName,
                        TrialBalanceLine::accountClass,
                        TrialBalanceLine::normalBalance,
                        TrialBalanceLine::debitTotal,
                        TrialBalanceLine::creditTotal,
                        TrialBalanceLine::entryCount)
                .containsExactly(
                        tuple("10100", "현금및현금성자산", GlAccountClass.ASSET, GlNormalBalance.DEBIT, 300L, 50L, 2L),
                        tuple("20100", "예수금", GlAccountClass.LIABILITY, GlNormalBalance.CREDIT, 50L, 1_000L, 3L),
                        tuple("50100", "이자비용", GlAccountClass.EXPENSE, GlNormalBalance.DEBIT, 700L, 0L, 1L));
    }

    @Test
    @DisplayName("계정과목에 없는 코드의 분개도 행으로 남고 이름·분류·정상잔액은 null 이다")
    void keepsUnregisteredAccountRow() {
        LocalDate day = LocalDate.of(2031, 1, 15);
        saveVoucher(day, 1, JournalEntry.debit("20100", 500L), JournalEntry.credit("20100", 500L));
        insertUnregisteredEntry(new VoucherNumber(day, GlTxType.TRANSFER, 1).value(), day, "19999", 400L);

        List<TrialBalanceLine> rows = adapter.sumByAccount(BAND_FROM, BAND_TO);

        assertThat(rows)
                .extracting(
                        TrialBalanceLine::accountCode,
                        TrialBalanceLine::accountName,
                        TrialBalanceLine::accountClass,
                        TrialBalanceLine::normalBalance,
                        TrialBalanceLine::debitTotal)
                .containsExactly(
                        tuple("19999", null, null, null, 400L),
                        tuple("20100", "예수금", GlAccountClass.LIABILITY, GlNormalBalance.CREDIT, 500L));
    }

    @Test
    @DisplayName("기간 안에 분개가 없으면 빈 목록")
    void emptyWhenNoEntries() {
        assertThat(adapter.sumByAccount(BAND_FROM, BAND_TO)).isEmpty();
    }

    /**
     * PH-28b 결함 주입처럼 FK 를 끄고 미등록 계정 분개를 넣는다. FOREIGN_KEY_CHECKS 는 세션 변수라 같은 커넥션에서
     * 끄고, 넣고, 다시 켠다.
     */
    private void insertUnregisteredEntry(String voucherNo, LocalDate tradeDate, String accountCode, long amount) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gl_journal_entry (voucher_no, line_no, account_code, dr_cr, amount, trade_date)"
                                + " VALUES (?, 99, ?, 'DEBIT', ?, ?)")) {
                    insert.setString(1, voucherNo);
                    insert.setString(2, accountCode);
                    insert.setLong(3, amount);
                    insert.setDate(4, Date.valueOf(tradeDate));
                    insert.executeUpdate();
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }
}
