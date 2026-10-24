package com.shinhan.corebank.gl.domain;

import java.util.Optional;

/**
 * 원장 거래와 그 전표가 맞지 않는 건.
 *
 * <p>전표의 차변 합계와 대변 합계가 각각 원장 금액과 같아야 한다. 한쪽만 보면 대변만 변조된 전표를 놓친다. 전표 패턴이
 * 차변 한 줄과 대변 한 줄이라(gl_journal_patterns.md §3-2~3-4) 두 합계가 모두 거래 금액이다.
 */
public record LedgerVoucherMismatch(
        String transactionNumber,
        String ledgerTransactionType,
        Kind kind,
        long ledgerAmount,
        long voucherDebitTotal,
        long voucherCreditTotal) {

    public enum Kind {
        /** 원장 거래는 있는데 전표가 없다(분개누락). */
        MISSING_VOUCHER,
        /** 전표는 있는데 차변 또는 대변 합계가 원장 금액과 다르다(금액변조, 편측기표, 중복기표). */
        AMOUNT_MISMATCH
    }

    /** @param voucher 같은 거래번호를 참조하는 전표. 없으면 {@code null} */
    public static Optional<LedgerVoucherMismatch> judge(LedgerTransaction transaction, VoucherTotals voucher) {
        if (voucher == null) {
            return Optional.of(new LedgerVoucherMismatch(
                    transaction.transactionNumber(),
                    transaction.ledgerTransactionType(),
                    Kind.MISSING_VOUCHER,
                    transaction.amount(),
                    0L,
                    0L));
        }
        if (voucher.debitTotal() != transaction.amount() || voucher.creditTotal() != transaction.amount()) {
            return Optional.of(new LedgerVoucherMismatch(
                    transaction.transactionNumber(),
                    transaction.ledgerTransactionType(),
                    Kind.AMOUNT_MISMATCH,
                    transaction.amount(),
                    voucher.debitTotal(),
                    voucher.creditTotal()));
        }
        return Optional.empty();
    }
}
