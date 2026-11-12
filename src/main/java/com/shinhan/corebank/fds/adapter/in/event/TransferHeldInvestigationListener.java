package com.shinhan.corebank.fds.adapter.in.event;

import com.shinhan.corebank.fds.application.service.FdsInvestigationJobService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 보류 커밋 뒤 Agent 호출 없이 조사 작업만 멱등 등록한다. */
@Component
@RequiredArgsConstructor
public class TransferHeldInvestigationListener {
    private final FdsInvestigationJobService service;

    @TransactionalEventListener
    public void on(TransferHeld event) {
        service.register(event);
    }
}
