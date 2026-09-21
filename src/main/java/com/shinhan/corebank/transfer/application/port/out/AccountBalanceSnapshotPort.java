package com.shinhan.corebank.transfer.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * 대사 배치(#378)가 원장 합계와 비교할 "화면상 잔액"(account.balance)을 조회한다.
 * 락을 잡지 않는 단순 조회 전용 포트다 — AccountLockPort는 이체 실행(락 획득·잔액 변경) 책임에
 * 집중하고, 대사처럼 잔액을 바꾸지 않는 조회는 이 포트로 분리한다.
 */
public interface AccountBalanceSnapshotPort {

    /** 존재하지 않는 계좌 ID는 결과 맵에서 생략된다. */
    Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds);
}
