package com.shinhan.corebank.fds.adapter.in.event;

import com.shinhan.corebank.fds.application.service.FdsInvestigationJobService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 보류 커밋 뒤 Agent 호출 없이 조사 작업만 멱등 등록한다. */
@Component
@RequiredArgsConstructor
public class TransferHeldInvestigationListener {
    private final FdsInvestigationJobService service;

    @ApplicationModuleListener
    public void on(TransferHeld event) {
        service.register(event);
    }
}
