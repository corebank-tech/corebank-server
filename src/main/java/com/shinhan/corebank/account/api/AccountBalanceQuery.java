package com.shinhan.corebank.account.api;

import java.util.Collection;
import java.util.Map;

// 다른 도메인이 계좌 저장소를 직접 참조하지 않고 잔액만 일괄 조회할 수 있게 공개한다.
public interface AccountBalanceQuery {

    // 존재하지 않는 계좌 ID는 결과 맵에서 생략된다.
    Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds);
}
