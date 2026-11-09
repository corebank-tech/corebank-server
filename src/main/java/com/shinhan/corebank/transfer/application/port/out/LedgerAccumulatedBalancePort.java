package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 원장에 기록된 일말잔액의 기간 합계를 계좌별로 집계한다.
 *
 * 날짜 기준은 기존 PH-11과 동일한 occurred_at이며,
 * 계산 구간은 [fromInclusive, toExclusive)이다.
 */
public interface LedgerAccumulatedBalancePort {

    Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive);
}
