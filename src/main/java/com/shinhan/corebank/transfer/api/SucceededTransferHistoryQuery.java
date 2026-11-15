package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객의 성공 이체를 목록과 수취인 요약으로 읽는 계약(#995). FDS Agent의 읽기 Tool이 보류 사건을 조사할 때 쓴다.
 *
 * 기준은 RecentTransferQuery와 같다. 당행 SUCCESS만 담고 정정 체인으로 무효화된 행과 취소정정은 뺀다. 고객의 모든 출금계좌가
 * 대상이다. 수취계좌번호는 원문이고 마스킹은 호출자가 한다.
 *
 * 훅 C 안에서 부르지 않는다. summarizeRecipient는 기간 하한 없이 고객 이력 전체를 읽어서, 고객 락을 쥔 채 부르면 같은 고객의
 * 이체가 그만큼 기다린다.
 */
public interface SucceededTransferHistoryQuery {

    /**
     * [fromInclusive, toExclusive) 성공 이체를 최신순으로 최대 limit건. 같은 시각이면 나중에 생긴 이체가 먼저다. 없으면 빈 목록.
     * limit이 0 이하이거나 fromInclusive가 toExclusive보다 늦거나 같으면 호출 결함으로 거부한다.
     */
    List<SucceededTransfer> findRecent(
            long customerId, LocalDateTime fromInclusive, LocalDateTime toExclusive, int limit);

    /** 이 수취계좌로 보낸 전체 기간 성공 이력 요약. excludeWithdrawalAccountId는 다른 출금계좌 수에서만 뺀다. */
    RecipientHistory summarizeRecipient(long customerId, String depositAccountNumber, long excludeWithdrawalAccountId);
}
