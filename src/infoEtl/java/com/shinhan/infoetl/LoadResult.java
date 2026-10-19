package com.shinhan.infoetl;

import java.time.LocalDate;

public record LoadResult(long loadBatchId, LocalDate baseDate, long balanceRowCount, long martBalanceTotal) {}
