package com.shinhan.corebank.common.event;

/**
 * BEFORE_COMMIT 시점에 이벤트를 어디에 남길지 정하는 자리. #437 아웃박스가 outbox 행 INSERT 로 구현한다.
 * 여기서 던진 예외는 업무 트랜잭션을 롤백시킨다 — 이벤트를 못 남기면 업무도 커밋되지 않는다.
 */
public interface DomainEventSink {

    void record(DomainEvent event);
}
