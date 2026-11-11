package com.shinhan.corebank.fds.adapter.in.scheduler;

import com.shinhan.corebank.fds.application.service.BehaviorScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 추론 시작 후 멈춘 점수 작업을 주기적으로 종료한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fds.score", name = "enabled", havingValue = "true")
public class BehaviorScoreRecoveryScheduler {
    private final BehaviorScoreService service;

    @Scheduled(fixedDelayString = "PT1M")
    public void recover() {
        service.recoverInterrupted();
    }
}
