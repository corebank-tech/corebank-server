package com.shinhan.corebank.fds.adapter.in.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.fds.application.service.FdsInvestigationJobService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransferHeldInvestigationListenerTest {
    @Test
    void delegatesDurableRegistrationWithoutCallingAgent() {
        FdsInvestigationJobService service = mock(FdsInvestigationJobService.class);
        TransferHeldInvestigationListener listener = new TransferHeldInvestigationListener(service);
        TransferHeld event = new TransferHeld(
                10L, 20L, "A", TransferHoldReleaseMode.ON_APPROVAL, null, null, LocalDateTime.of(2026, 11, 14, 4, 0));

        listener.on(event);

        verify(service).register(event);
    }
}
