package com.shinhan.corebank.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 발행한 트랜잭션의 커밋 직전에 sink 로 넘긴다.
 *
 * <p>AFTER_COMMIT 이 아닌 이유: 커밋이 끝난 뒤 프로세스가 죽으면 이벤트가 사라진다. BEFORE_COMMIT 은
 * 같은 트랜잭션 안이라 업무 데이터와 이벤트가 함께 남거나 함께 없어진다.
 *
 * <p>활성 트랜잭션이 없으면 호출되지 않는다(fallbackExecution 기본값 false). 그래서 발행은 반드시
 * 업무 트랜잭션 안에서 해야 한다 — TransactionTemplate 으로 연 트랜잭션도 된다.
 */
@Component
@RequiredArgsConstructor
public class DomainEventBeforeCommitListener {

    private final DomainEventSink sink;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(DomainEvent event) {
        sink.record(event);
    }
}
