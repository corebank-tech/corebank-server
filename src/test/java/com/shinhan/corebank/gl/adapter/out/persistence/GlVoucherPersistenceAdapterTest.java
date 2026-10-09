package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("전표 저장(GlVoucherPersistenceAdapter) 통합 테스트")
class GlVoucherPersistenceAdapterTest extends IntegrationTestSupport {

    // 시드(PH-60b)와 날짜가 겹치지 않도록 먼 미래 거래일만 쓴다.
    private static final LocalDate TRADE_DATE = LocalDate.of(2099, 1, 6);

    @Autowired
    private GlVoucherPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", TRADE_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", TRADE_DATE);
    }

    @Test
    @DisplayName("개시 잔액 전표를 넣은 직후 계정별 합계가 입력과 같고 장부 전체 차변 합계와 대변 합계가 같다")
    void openingVoucherBalancesTrialBalance() {
        Voucher opening = Voucher.opening(new VoucherNumber(TRADE_DATE, GlTxType.OPENING, 1), 5_000_000L, 3_200_000L);

        adapter.save(opening);

        // 어댑터 트랜잭션이 커밋된 뒤 JDBC 로 다시 읽는다 — 영속성 컨텍스트를 거치지 않는다.
        List<Map<String, Object>> byAccount = jdbcTemplate.queryForList(
                """
                SELECT account_code,
                       SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END) AS debit_total,
                       SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
                FROM gl_journal_entry
                WHERE trade_date = ?
                GROUP BY account_code
                ORDER BY account_code
                """,
                TRADE_DATE);
        assertThat(byAccount)
                .extracting(
                        row -> row.get("account_code"),
                        row -> ((Number) row.get("debit_total")).longValue(),
                        row -> ((Number) row.get("credit_total")).longValue())
                .containsExactly(
                        tuple("10100", 5_000_000L, 0L), tuple("20100", 0L, 3_200_000L), tuple("30100", 0L, 1_800_000L));

        Map<String, Object> total = jdbcTemplate.queryForMap(
                """
                SELECT SUM(CASE WHEN dr_cr = 'DEBIT'  THEN amount ELSE 0 END) AS debit_total,
                       SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
                FROM gl_journal_entry
                WHERE trade_date = ?
                """,
                TRADE_DATE);
        assertThat(((Number) total.get("debit_total")).longValue())
                .isEqualTo(((Number) total.get("credit_total")).longValue());
    }

    @Test
    @DisplayName("이미 있는 전표번호로 저장하면 전표 PK 위반으로 실패하고 기존 전표는 그대로다")
    void rejectsDuplicateVoucherNumberAtVoucherPrimaryKey() {
        VoucherNumber number = new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 43);
        adapter.save(Voucher.create(
                number,
                "20990106WB0000000043",
                "시드 거래",
                List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 10_000L))));

        Voucher duplicate = Voucher.create(
                number,
                "20990106WB0000000044",
                "런타임 거래",
                List.of(JournalEntry.debit("20100", 500L), JournalEntry.credit("20100", 500L)));

        // 분개 줄 유니크 키(uk_gl_journal_entry_line)가 아니라 전표 PK 에서 막혀야 원인이 로그에 바로 드러난다.
        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseMessage("Duplicate entry '20990106-TRF-000043' for key 'gl_voucher.PRIMARY'");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT description FROM gl_voucher WHERE voucher_no = ?", String.class, number.value()))
                .isEqualTo("시드 거래");
    }

    @Test
    @DisplayName("같은 유형·참조 키로 다른 전표번호를 저장하면 GLA9008")
    void rejectsDuplicateReferenceKeyOfSameType() {
        String referenceKey = "20990106WB0000000050";
        adapter.save(Voucher.create(
                new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 50),
                referenceKey,
                null,
                List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 10_000L))));

        Voucher sameKey = Voucher.create(
                new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 51),
                referenceKey,
                null,
                List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 10_000L)));

        assertThatThrownBy(() -> adapter.save(sameKey))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlErrorCode.DUPLICATE_REFERENCE_KEY);
    }

    @Test
    @DisplayName("전표와 분개 줄이 줄 번호·거래일과 함께 저장된다")
    void savesVoucherWithNumberedLines() {
        Voucher transfer = Voucher.create(
                new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 7),
                "20990106WB0000000007",
                null,
                List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 10_000L)));

        adapter.save(transfer);

        Map<String, Object> voucher = jdbcTemplate.queryForMap(
                "SELECT trade_date, tx_type FROM gl_voucher WHERE voucher_no = ?", "20990106-TRF-000007");
        assertThat(voucher.get("tx_type")).isEqualTo("TRANSFER");
        assertThat(voucher.get("trade_date").toString()).isEqualTo("2099-01-06");

        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT line_no, dr_cr, trade_date FROM gl_journal_entry WHERE voucher_no = ? ORDER BY line_no",
                "20990106-TRF-000007");
        assertThat(lines)
                .extracting(row -> ((Number) row.get("line_no")).intValue(), row -> row.get("dr_cr"))
                .containsExactly(tuple(1, "DEBIT"), tuple(2, "CREDIT"));
        assertThat(lines)
                .allSatisfy(row -> assertThat(row.get("trade_date").toString()).isEqualTo("2099-01-06"));
    }
}
