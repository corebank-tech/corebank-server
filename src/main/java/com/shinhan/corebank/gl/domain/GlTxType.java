package com.shinhan.corebank.gl.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전표 유형. 전표번호 접두의 근거가 된다(채번 규칙은 PH-21).
 *
 * <p>타행 미결제는 분개 패턴이 확정되지 않아 넣지 않았다 — 소유는 P4(PH-33)다. 추가할 때
 * {@code gl_voucher} 의 {@code ck_gl_voucher_tx_type} 도 새 V 파일로 함께 넓혀야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum GlTxType {
    OPENING("OPN"),
    TRANSFER("TRF"),
    PRODUCT_SUBSCRIPTION("SUB"),
    INTEREST("INT");

    /** 전표번호 가운데 3자리. docs/phase2/gl_journal_patterns.md §1. */
    private final String voucherCode;
}
