package com.shinhan.corebank.fds.adapter.in.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.fds.application.service.FdsAdminActionService;
import org.junit.jupiter.api.Test;

class FdsAdminActionRecoverySchedulerTest {
    @Test
    void startupAndPeriodicTriggersUseSameRecoveryBoundary() {
        FdsAdminActionService actions = mock(FdsAdminActionService.class);
        FdsAdminActionRecoveryScheduler scheduler = new FdsAdminActionRecoveryScheduler(actions);

        scheduler.onReady();
        scheduler.recover();

        verify(actions, times(2)).recoverPending();
    }
}
