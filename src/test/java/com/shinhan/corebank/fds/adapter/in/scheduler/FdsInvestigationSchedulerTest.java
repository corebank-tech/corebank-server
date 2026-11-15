package com.shinhan.corebank.fds.adapter.in.scheduler;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.service.FdsInvestigationJobService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FdsInvestigationSchedulerTest {

    @Test
    void recoversExpiredLeasesBeforeRunningDueJobs() {
        FdsInvestigationJobService service = mock(FdsInvestigationJobService.class);
        FdsInvestigationAgentPort agent = mock(FdsInvestigationAgentPort.class);
        FdsInvestigationScheduler scheduler = new FdsInvestigationScheduler(service, agent);

        scheduler.run();

        InOrder ordered = inOrder(service);
        ordered.verify(service).recoverExpired();
        ordered.verify(service).runDue(agent, 20);
    }
}
