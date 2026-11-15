package com.shinhan.corebank.fds.adapter.in.scheduler;

import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.service.FdsInvestigationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료 임대를 복구하고 실행 가능한 Agent 조사 작업을 짧게 선점한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(FdsInvestigationAgentPort.class)
@ConditionalOnProperty(prefix = "app.fds.investigation", name = "enabled", havingValue = "true")
public class FdsInvestigationScheduler {
    private final FdsInvestigationJobService service;
    private final FdsInvestigationAgentPort agent;

    @Scheduled(fixedDelayString = "PT1M")
    public void run() {
        service.recoverExpired();
        service.runDue(agent, 20);
    }
}
