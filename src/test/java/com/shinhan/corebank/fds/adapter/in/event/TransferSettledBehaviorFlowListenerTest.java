package com.shinhan.corebank.fds.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferSettled;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(
            value = FdsErrorCode.class,
            names = {"NOT_FOUND", "FLOW_CONFLICT"})
    void permanentOwnershipOrConflictFailureIsConsumedWithoutOverwriteRetry(FdsErrorCode errorCode) {
        when(mappings.save(101, "flow-1", 7)).thenThrow(new BusinessException(errorCode));

        listener.on(event(ProcessResultStatus.SUCCESS, "flow-1"));

        verify(mappings).save(101, "flow-1", 7);
    }

    @Test
    void otherBusinessFailureIsPropagatedForRedelivery() {
        BusinessException failure = new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        when(mappings.save(101, "flow-1", 7)).thenThrow(failure);

        assertThatThrownBy(() -> listener.on(event(ProcessResultStatus.SUCCESS, "flow-1")))
                .isSameAs(failure);
    }

    @Test
    void infrastructureFailureIsPropagatedForRedelivery() {
        IllegalStateException failure = new IllegalStateException("mapping DB unavailable");
        when(mappings.save(101, "flow-1", 7)).thenThrow(failure);

        assertThatThrownBy(() -> listener.on(event(ProcessResultStatus.SUCCESS, "flow-1")))
                .isSameAs(failure);
    }

    private TransferSettled event(ProcessResultStatus status, String flowId) {
        return new TransferSettled(7L, 101L, "IMMEDIATE_TRANSFER", status, null, LocalDateTime.now(), flowId, null);
    }
}
