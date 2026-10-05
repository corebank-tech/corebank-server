package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * 전표 — 분개를 담는 단위.
 *
 * <p>차변 합계와 대변 합계가 같아야 만들어진다. 이 불변식은 도메인에서만 강제하고 DB 제약은 걸지 않는다.
 * MySQL 의 CHECK 는 행 하나만 보고 지연 제약이 없어 여러 줄의 합을 저장 시점에 막을 수 없고, PH-28b 가
 * 편측기표·금액변조를 직접 주입해 탐지율을 재기 때문이다. 저장된 뒤의 불일치는
 * docs/phase2/gl_journal_patterns.md §5 검증 SQL 이 찾는다. Apache Fineract 도 같은 방식이다
 * ({@code JournalEntryWritePlatformServiceImpl.checkDebitAndCreditAmounts}, DB 제약 없음).
 */
@Getter
public final class Voucher {

    private static final String CASH_ACCOUNT = "10100";
    private static final String DEPOSIT_ACCOUNT = "20100";
    private static final String OPENING_EQUITY_ACCOUNT = "30100";

    private final VoucherNumber number;
    private final String description;
    private final List<JournalEntry> entries;

    private Voucher(VoucherNumber number, String description, List<JournalEntry> entries) {
        this.number = Objects.requireNonNull(number, "number must not be null");
        this.description = description;
        this.entries = List.copyOf(entries);
        validateBalanced(this.entries);
    }

    public static Voucher create(VoucherNumber number, String description, List<JournalEntry> entries) {
        return new Voucher(number, description, entries);
    }

    /**
     * 개시 잔액 전표 (gl_journal_patterns.md §3-1). 차 현금성 / 대 예수금(고객 잔액 합계) + 대 개시잔액(차액).
     * 0원 줄은 만들지 않는다.
     */
    public static Voucher opening(VoucherNumber number, long cashTotal, long customerDepositTotal) {
        if (number.txType() != GlTxType.OPENING) {
            throw new BusinessException(GlErrorCode.VOUCHER_TYPE_MISMATCH);
        }
        if (cashTotal < customerDepositTotal) {
            throw new BusinessException(GlErrorCode.OPENING_CASH_BELOW_DEPOSITS);
        }
        List<JournalEntry> entries = new ArrayList<>();
        if (cashTotal > 0) {
            entries.add(JournalEntry.debit(CASH_ACCOUNT, cashTotal));
        }
        if (customerDepositTotal > 0) {
            entries.add(JournalEntry.credit(DEPOSIT_ACCOUNT, customerDepositTotal));
        }
        long equity = cashTotal - customerDepositTotal;
        if (equity > 0) {
            entries.add(JournalEntry.credit(OPENING_EQUITY_ACCOUNT, equity));
        }
        return new Voucher(number, "개시 잔액", entries);
    }

    public LocalDate getTradeDate() {
        return number.tradeDate();
    }

    public GlTxType getTxType() {
        return number.txType();
    }

    private static void validateBalanced(List<JournalEntry> entries) {
        if (entries.size() < 2) {
            throw new BusinessException(GlErrorCode.TOO_FEW_JOURNAL_ENTRIES);
        }
        long debitTotal = 0;
        long creditTotal = 0;
        for (JournalEntry entry : entries) {
            if (entry.drCr() == JournalDirection.DEBIT) {
                debitTotal = Math.addExact(debitTotal, entry.amount());
            } else {
                creditTotal = Math.addExact(creditTotal, entry.amount());
            }
        }
        if (debitTotal != creditTotal) {
            throw new BusinessException(GlErrorCode.UNBALANCED_VOUCHER);
        }
    }
}
