package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.gl.api.GlTxType;

/** 전표 하나의 차변·대변 합계. 분개가 하나도 없으면 둘 다 0 이다. */
public record VoucherTotals(GlTxType txType, String referenceKey, long debitTotal, long creditTotal) {}
