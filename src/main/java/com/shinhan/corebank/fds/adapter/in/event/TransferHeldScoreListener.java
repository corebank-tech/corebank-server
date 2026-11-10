package com.shinhan.corebank.fds.adapter.in.event;

import com.shinhan.corebank.fds.application.service.BehaviorScoreService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** P4 보류 커밋 후 전달된 사건의 점수 계산을 시작한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fds.score", name = "enabled", havingValue = "true")
public class TransferHeldScoreListener {
    private final BehaviorScoreService service;

    @Async
    @TransactionalEventListener
    public void on(TransferHeld event) {
        service.onHeld(event);
    }
}
