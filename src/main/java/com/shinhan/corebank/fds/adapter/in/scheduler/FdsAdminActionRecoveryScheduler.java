package com.shinhan.corebank.fds.adapter.in.scheduler;

import com.shinhan.corebank.fds.application.service.FdsAdminActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 응답 유실과 재시작 뒤에도 PENDING 관리자 조치를 P4 정본으로 복구한다. */
@Component
@RequiredArgsConstructor
public class FdsAdminActionRecoveryScheduler {
    private final FdsAdminActionService actions;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        actions.recoverPending();
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void recover() {
        actions.recoverPending();
    }
}
