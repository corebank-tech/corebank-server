package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort.UnbalancedVouchers;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.UnbalancedVoucher;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("GL 정합성 점검 쿼리(GlIntegrityQueryAdapter) 통합 테스트")
class GlIntegrityQueryAdapterTest extends IntegrationTestSupport {

    // 시드와 다른 GL 테스트가 쓰지 않는 거래일 대역(2031-07).
    private static final LocalDate DAY = LocalDate.of(2031, 7, 14);

    @Autowired
    private GlIntegrityQueryAdapter adapter;

    @Autowired
    private GlVoucherPersistenceAdapter voucherPersistenceAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", DAY);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", DAY);
    }

    @Test
    @DisplayName("차대변 불일치 전표는 전표번호 순으로 limit 건까지 담고 건수는 전체를 센다. 다른 거래일과 정상 전표는 뺀다")
    void listsUpToLimitAndCountsAll() {
        for (int sequence = 1; sequence <= 4; sequence++) {
            saveVoucher(DAY, sequence);
        }
        saveVoucher(DAY.plusDays(1), 1);
        // 1·2·3번 전표의 대변을 지워 편측기표로 만든다. 4번은 정상으로 둔다.
        jdbcTemplate.update(
                "DELETE FROM gl_journal_entry WHERE trade_date = ? AND dr_cr = 'CREDIT' AND voucher_no <> ?",
                DAY,
                new VoucherNumber(DAY, GlTxType.TRANSFER, 4).value());

        UnbalancedVouchers result = adapter.findUnbalancedVouchers(DAY, 2);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.vouchers())
                .extracting(UnbalancedVoucher::voucherNo)
                .containsExactly(
                        new VoucherNumber(DAY, GlTxType.TRANSFER, 1).value(),
                        new VoucherNumber(DAY, GlTxType.TRANSFER, 2).value());
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", DAY.plusDays(1));
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", DAY.plusDays(1));
    }

    private void saveVoucher(LocalDate tradeDate, int sequence) {
        VoucherNumber number = new VoucherNumber(tradeDate, GlTxType.TRANSFER, sequence);
        voucherPersistenceAdapter.save(Voucher.create(
                number,
                "PH28B-" + number.value(),
                null,
                List.of(JournalEntry.debit("20100", 100L), JournalEntry.credit("20100", 100L))));
    }
}
