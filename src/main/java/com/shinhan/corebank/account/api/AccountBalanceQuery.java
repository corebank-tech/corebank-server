package com.shinhan.corebank.account.api;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;

// 다른 도메인이 계좌 저장소를 직접 참조하지 않고 잔액만 일괄 조회할 수 있게 공개한다.
public interface AccountBalanceQuery {

    // 존재하지 않는 계좌 ID는 결과 맵에서 생략된다.
    Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds);

    // 전 계좌를 차례로 훑는 keyset 페이지(transfer 전수 대사, #468). afterAccountId보다 큰 계좌를
    // account_id 오름차순으로 최대 limit개 돌려준다. 다음 커서는 lastKey(), 빈 맵이면 끝이다.
    SortedMap<Long, Long> findBalancesAfter(long afterAccountId, int limit);
}
