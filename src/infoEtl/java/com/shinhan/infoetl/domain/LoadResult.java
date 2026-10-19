package com.shinhan.infoetl.domain;

import java.time.LocalDate;

public record LoadResult(
        long loadBatchId,
        LocalDate baseDate,
        long balanceRowCount,
        long eventRowCount,
        long martBalanceTotal,
        LocalDate asOfDate) {}
