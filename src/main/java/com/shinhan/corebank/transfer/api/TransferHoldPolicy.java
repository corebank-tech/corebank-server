package com.shinhan.corebank.transfer.api;

/**
 * 이체 확장점 훅 C — 즉시이체를 바로 실행할지 보류할지 정할 기회를 다른 도메인에 연다(#771, transfer_seam.md §9).
 *
 * 구현체를 빈으로 등록하면 {@code TransferExecutionService}가 기표 트랜잭션 안, 한도 적립 직후에 {@link #order()} 오름차순으로
 * 부른다. 처음 보류를 낸 구현체가 정하고 나머지는 부르지 않는다. 고객이 요청한 즉시이체만 지나고 예약·자동이체와 보류 건 실행은 지나지 않는다.
 *
 * 한도 적립의 고객 단위 행 락을 쥔 채 돈다. 외부 호출과 쓰기를 하지 않고 같은 DB 읽기만 한다. 예외를 던지면 이체는 ERROR로 확정된다.
 * 전역 스위치가 꺼져 있으면 DB를 읽지 않고 {@link TransferHoldDecision#proceed()}를 돌려준다(성능 측정을 바꾸지 않기 위해서다).
 */
public interface TransferHoldPolicy {

    /** 작을수록 먼저 실행한다. 승인으로 푸는 정책이 시각으로 푸는 정책보다 작게 둔다. */
    int order();

    TransferHoldDecision decide(TransferHoldContext context);
}
