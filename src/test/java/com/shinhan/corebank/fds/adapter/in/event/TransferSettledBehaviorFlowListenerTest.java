package com.shinhan.corebank.fds.adapter.in.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferSettled;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransferSettledBehaviorFlowListenerTest {

    private final BehaviorFlowMappingUseCase mappings = mock(BehaviorFlowMappingUseCase.class);
    private final TransferSettledBehaviorFlowListener listener = new TransferSettledBehaviorFlowListener(mappings);

    @Test
    void successfulTransferWithFlowIsMapped() {
        listener.on(event(ProcessResultStatus.SUCCESS, "flow-1"));

        verify(mappings).save(101, "flow-1", 7);
    }

    @Test
    void errorOrMissingFlowDoesNotCreateMapping() {
        listener.on(event(ProcessResultStatus.ERROR, "flow-1"));
        listener.on(event(ProcessResultStatus.SUCCESS, null));

        verifyNoInteractions(mappings);
    }

    @Test
    void permanentOwnershipOrConflictFailureIsConsumedWithoutOverwriteRetry() {
        when(mappings.save(101, "flow-1", 7)).thenThrow(new BusinessException(FdsErrorCode.FLOW_CONFLICT));

        listener.on(event(ProcessResultStatus.SUCCESS, "flow-1"));

        verify(mappings).save(101, "flow-1", 7);
    }

    private TransferSettled event(ProcessResultStatus status, String flowId) {
        return new TransferSettled(7L, 101L, "IMMEDIATE_TRANSFER", status, null, LocalDateTime.now(), flowId, null);
    }
}
