package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort;
import com.shinhan.corebank.gl.domain.UnbalancedVoucher;
import com.shinhan.corebank.gl.domain.VoucherTotals;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlIntegrityQueryAdapter implements GlIntegrityQueryPort {

    // 거래일 하루치 전표는 전표번호 접두(yyyyMMdd-)로 고른다. gl_voucher·gl_journal_entry 에 거래일 인덱스가 없어서(GL-IDX 대기)
    // 전표번호 PK 범위 스캔을 쓴다. 전표번호 날짜와 거래일이 같다는 것은 gl_journal_patterns.md §5 (5)가 보장한다.
    //
    // LEFT JOIN 이라 분개가 한 줄도 없는 전표도 entry_count 0 으로 잡힌다. PK 하나로만 묶어 PK 순서대로 흘려 집계하게 한다.
    // 창 함수로 전체 건수를 같이 내면 하루치 전표를 임시 테이블에 모은 뒤 집계해 60b 9/25 기준 14.5초가 걸렸다(이 형태 3.4초).
    // 건수는 결과를 읽으며 센다 — 결과는 결함 전표뿐이라 평소 0행이고, 별도 COUNT 쿼리는 목록과 어긋날 수 있다.
    static final String UNBALANCED_VOUCHERS_SQL =
            """
            SELECT v.voucher_no,
                   v.tx_type,
                   v.reference_key,
                   COALESCE(SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount END), 0) AS debit_total,
                   COALESCE(SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount END), 0) AS credit_total,
                   COUNT(je.voucher_no)                                               AS entry_count
            FROM gl_voucher v
            LEFT JOIN gl_journal_entry je ON je.voucher_no = v.voucher_no
            WHERE v.voucher_no LIKE :voucherNoPrefix
            GROUP BY v.voucher_no
            HAVING debit_total <> credit_total OR entry_count < 2
            ORDER BY v.voucher_no
            """;

    // uk_gl_voucher_tx_type_reference_key 로 전표를 찾고 uk_gl_journal_entry_line 으로 분개를 붙인다. 같은 거래일 전표만 짝으로 본다.
    static final String VOUCHER_TOTALS_SQL =
            """
            SELECT v.tx_type,
                   v.reference_key,
                   COALESCE(SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount END), 0) AS debit_total,
                   COALESCE(SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount END), 0) AS credit_total
            FROM gl_voucher v
            LEFT JOIN gl_journal_entry je ON je.voucher_no = v.voucher_no
            WHERE v.tx_type IN (:txTypes)
              AND v.reference_key IN (:referenceKeys)
              AND v.voucher_no LIKE :voucherNoPrefix
            GROUP BY v.voucher_no, v.tx_type, v.reference_key
            """;

    static final String COUNT_VOUCHERS_SQL =
            """
            SELECT COUNT(*)
            FROM gl_voucher v
            WHERE v.voucher_no LIKE :voucherNoPrefix
              AND v.tx_type IN (:txTypes)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public UnbalancedVouchers findUnbalancedVouchers(LocalDate tradeDate, int limit) {
        long[] totalCount = {0L};
        List<UnbalancedVoucher> vouchers = new ArrayList<>();
        jdbcTemplate.query(UNBALANCED_VOUCHERS_SQL, Map.of("voucherNoPrefix", voucherNoPrefix(tradeDate)), rs -> {
            totalCount[0]++;
            if (vouchers.size() < limit) {
                vouchers.add(new UnbalancedVoucher(
                        rs.getString("voucher_no"),
                        GlTxType.valueOf(rs.getString("tx_type")),
                        rs.getString("reference_key"),
                        rs.getLong("debit_total"),
                        rs.getLong("credit_total"),
                        rs.getLong("entry_count")));
            }
        });
        return new UnbalancedVouchers(vouchers, totalCount[0]);
    }

    @Override
    public List<VoucherTotals> sumVoucherTotals(
            LocalDate tradeDate, Collection<GlTxType> txTypes, Collection<String> referenceKeys) {
        if (txTypes.isEmpty() || referenceKeys.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                VOUCHER_TOTALS_SQL,
                Map.of(
                        "txTypes", names(txTypes),
                        "referenceKeys", referenceKeys,
                        "voucherNoPrefix", voucherNoPrefix(tradeDate)),
                (rs, rowNum) -> new VoucherTotals(
                        GlTxType.valueOf(rs.getString("tx_type")),
                        rs.getString("reference_key"),
                        rs.getLong("debit_total"),
                        rs.getLong("credit_total")));
    }

    @Override
    public long countVouchers(LocalDate tradeDate, Collection<GlTxType> txTypes) {
        if (txTypes.isEmpty()) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(
                COUNT_VOUCHERS_SQL,
                Map.of("voucherNoPrefix", voucherNoPrefix(tradeDate), "txTypes", names(txTypes)),
                Long.class);
        return count == null ? 0L : count;
    }

    private static String voucherNoPrefix(LocalDate tradeDate) {
        return tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-%";
    }

    private static List<String> names(Collection<GlTxType> txTypes) {
        return txTypes.stream().map(GlTxType::name).toList();
    }
}
