package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 고객이 한 수취계좌로 보낸 성공 이체를 읽는 계약(#771). 훅 C 구현체가 반복 이체를 판정할 때 쓴다.
 *
 * 커밋된 SUCCESS 행만 센다. 지금 판정 중인 요청은 아직 행이 없어 들어가지 않는다. 정정 체인으로 무효화된 행과 타행이체는 뺀다.
 * 기준은 고객의 모든 출금계좌다. 기존 인덱스 ix_transfer_wacc로 읽고 인덱스를 새로 달지 않는다.
 *
 * 호출자 트랜잭션에 참여한다. 훅 C 안에서 부르면 같은 고객의 앞선 요청이 커밋한 결과까지 보인다.
 */
public interface RecentTransferQuery {

    /** since 이후(포함) 성공 이체의 건수·합계·첫 시각. 없으면 건수 0. */
    RecentTransferAggregate aggregateSucceeded(long customerId, String depositAccountNumber, LocalDateTime since);

    /** before 이전(미포함)에 성공 이체가 한 건이라도 있는지. */
    boolean hasSucceededBefore(long customerId, String depositAccountNumber, LocalDateTime before);
}
