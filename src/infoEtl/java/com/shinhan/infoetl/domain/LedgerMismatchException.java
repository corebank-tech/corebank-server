package com.shinhan.infoetl.domain;

// 마트 잔액 합계가 원장 합계와 다르면 그 날 적재는 되돌린다(설계 §4 원장 대조).
public final class LedgerMismatchException extends RuntimeException {

    private final long ledgerTotal;
    private final long martBalanceTotal;

    public LedgerMismatchException(long ledgerTotal, long martBalanceTotal) {
        super("마트 잔액 합계가 원장 합계와 다릅니다: ledgerTotal=" + ledgerTotal + ", martBalanceTotal=" + martBalanceTotal);
        this.ledgerTotal = ledgerTotal;
        this.martBalanceTotal = martBalanceTotal;
    }

    public long ledgerTotal() {
        return ledgerTotal;
    }

    public long martBalanceTotal() {
        return martBalanceTotal;
    }
}
