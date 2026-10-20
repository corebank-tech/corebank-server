package com.shinhan.infoetl.domain;

import java.time.LocalDate;

// asOfDate는 첫 적재일이 COMPLETED가 아니면 null이다.
public record LoadResult(
        long loadBatchId, LocalDate baseDate, long balanceRowCount, long martBalanceTotal, LocalDate asOfDate) {}
