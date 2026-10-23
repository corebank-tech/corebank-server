package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.gl.api.GlTxType;

/** 차변 합계와 대변 합계가 다르거나 분개가 2줄 미만인 전표(gl_journal_patterns.md §5 (1)·(3)). */
public record UnbalancedVoucher(
        String voucherNo, GlTxType txType, String referenceKey, long debitTotal, long creditTotal, long entryCount) {}
