package com.shinhan.corebank.gl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.LedgerVoucherMismatch.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("원장↔전표 판정(LedgerVoucherMismatch)")
class LedgerVoucherMismatchTest {

    private static final LedgerTransaction TRANSFER =
            new LedgerTransaction("20261001WB0000000001", "IMMEDIATE_TRANSFER", 1_000L);

    @Test
    @DisplayName("전표가 없으면 MISSING_VOUCHER 이고 전표 합계는 0 이다")
    void missingVoucher() {
        assertThat(LedgerVoucherMismatch.judge(TRANSFER, null))
                .hasValue(new LedgerVoucherMismatch(
                        "20261001WB0000000001", "IMMEDIATE_TRANSFER", Kind.MISSING_VOUCHER, 1_000L, 0L, 0L));
    }

    @Test
    @DisplayName("차변·대변 합계가 모두 원장 금액과 같으면 불일치가 아니다")
    void matches() {
        assertThat(LedgerVoucherMismatch.judge(TRANSFER, totals(1_000L, 1_000L)))
                .isEmpty();
    }

    @Test
    @DisplayName("대변만 다르거나(금액변조·편측기표), 둘 다 원장의 두 배여도(중복기표) AMOUNT_MISMATCH 다")
    void amountMismatch() {
        assertThat(LedgerVoucherMismatch.judge(TRANSFER, totals(1_000L, 900L)))
                .get()
                .extracting(LedgerVoucherMismatch::kind)
                .isEqualTo(Kind.AMOUNT_MISMATCH);
        assertThat(LedgerVoucherMismatch.judge(TRANSFER, totals(1_000L, 0L)))
                .get()
                .extracting(LedgerVoucherMismatch::kind)
                .isEqualTo(Kind.AMOUNT_MISMATCH);
        assertThat(LedgerVoucherMismatch.judge(TRANSFER, totals(2_000L, 2_000L)))
                .hasValue(new LedgerVoucherMismatch(
                        "20261001WB0000000001", "IMMEDIATE_TRANSFER", Kind.AMOUNT_MISMATCH, 1_000L, 2_000L, 2_000L));
    }

    private static VoucherTotals totals(long debit, long credit) {
        return new VoucherTotals(GlTxType.TRANSFER, "20261001WB0000000001", debit, credit);
    }
}
