package com.shinhan.corebank.gl.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 전표 1건 기표 요청. 헤더 금액은 두지 않는다 — 전표 합계는 줄에서 계산한다.
 *
 * @param txType       전표 유형. 전표번호 접두와 {@code gl_voucher.tx_type} 이 된다
 * @param referenceKey 원 거래번호. 원장·이체와 전표를 잇는 키이고 {@code (txType, referenceKey)} 로 유일하다
 * @param tradeDate    거래일(귀속 영업일)
 * @param lines        분개 줄. 차변 합계와 대변 합계가 같아야 한다
 */
public record JournalRequest(GlTxType txType, String referenceKey, LocalDate tradeDate, List<JournalLine> lines) {}
