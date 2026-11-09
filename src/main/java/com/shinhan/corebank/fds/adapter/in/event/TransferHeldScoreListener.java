package com.shinhan.corebank.fds.adapter.in.event;

import com.shinhan.corebank.fds.application.service.BehaviorScoreService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** P4 보류 커밋 후 전달된 사건의 점수 계산을 시작한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fds.score", name = "enabled", havingValue = "true")
public class TransferHeldScoreListener {
    private final BehaviorScoreService service;

    @ApplicationModuleListener
    public void on(TransferHeld event) {
        service.onHeld(event);
    }
}
