package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 전표번호 {@code yyyyMMdd-TTT-NNNNNN} (19자). 규칙의 정본은 docs/phase2/gl_journal_patterns.md §1.
 *
 * <p>거래일과 유형을 번호 안에 들고 있으므로 {@link Voucher} 는 이 둘을 따로 받지 않는다 — 번호와
 * 전표의 거래일·유형이 어긋나는 상태를 만들 수 없게 한다(패턴 문서 §5 검증 SQL (5)·(6)).
 */
public record VoucherNumber(LocalDate tradeDate, GlTxType txType, int sequence) {

    public static final int MAX_SEQUENCE = 999_999;

    public VoucherNumber {
        Objects.requireNonNull(tradeDate, "tradeDate must not be null");
        Objects.requireNonNull(txType, "txType must not be null");
        // 0 이하는 채번이 아니라 호출 코드의 버그라 범위 초과(GLA9005)와 나눈다.
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1: " + sequence);
        }
        if (sequence > MAX_SEQUENCE) {
            throw new BusinessException(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
        }
    }

    public String value() {
        return tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + txType.getVoucherCode()
                + "-" + String.format("%06d", sequence);
    }

    @Override
    public String toString() {
        return value();
    }
}
