package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 대사 배치(#378)가 원장(ledger_entry)에서 "이 계좌는 원장상 얼마여야 하는가"를 구하기 위해
 * 필요한 조회를 공개한다.
 */
public interface LedgerReconciliationPort {

    /** 지정 기간에 원장 기표가 있었던 계좌 ID를 중복 없이 반환한다 (증분 대사 대상 선별). */
    List<Long> findAccountIdsPostedBetween(LocalDateTime fromInclusive, LocalDateTime toExclusive);

    /** 계좌별 원장 누적 합계(입금 - 출금)를 반환한다. 결과에 없는 계좌는 원장 기표가 없었다는 뜻이다. */
    Map<Long, Long> sumSignedAmountByAccountIds(Collection<Long> accountIds);
}
