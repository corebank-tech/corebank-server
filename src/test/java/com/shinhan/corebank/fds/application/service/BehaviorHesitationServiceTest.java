package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorFlowMapping;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Event;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BehaviorHesitationServiceTest {

    private final BehaviorFlowPort flows = mock(BehaviorFlowPort.class);
    private final BehaviorFlowMappingUseCase mappings = mock(BehaviorFlowMappingUseCase.class);
    private final BehaviorHesitationService service = new BehaviorHesitationService(flows, mappings);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 12, 1, 0);

    @Test
    void currentFinalizedFlowIsHesitantWithTwoConfirmedSignals() {
        givenFlow("current", 7, 65, "STEP_BACK");

        assertThat(service.anyHesitation(7, "current", List.of())).isTrue();
    }

    @Test
    void previousSuccessfulTransferFlowCanSupplyHesitation() {
        when(mappings.findByRefId(11)).thenReturn(new BehaviorFlowMapping(11, "past", 7));
        givenFlow("past", 7, null, "STEP_BACK", "AMOUNT_EDITED", "AMOUNT_EDITED");

        assertThat(service.anyHesitation(7, null, List.of(11L))).isTrue();
    }

    @Test
    void missingIncompleteOrSingleSignalFlowDoesNotInventHesitation() {
        when(mappings.findByRefId(11)).thenReturn(null);
        when(flows.find("incomplete")).thenReturn(new Flow("incomplete", 7, now, null, 65));
        when(flows.find("single")).thenReturn(new Flow("single", 7, now, now, null));
        when(flows.events("single"))
                .thenReturn(List.of(event(1, "TRANSFER_ENTER"), event(2, "STEP_BACK"), event(3, "SUBMIT_REQUESTED")));

        assertThat(service.anyHesitation(7, null, List.of(11L))).isFalse();
        assertThat(service.anyHesitation(7, "incomplete", List.of())).isFalse();
        assertThat(service.anyHesitation(7, "single", List.of())).isFalse();
    }

    @Test
    void currentFlowOwnedByAnotherCustomerIsRejectedWithoutDisclosure() {
        when(flows.find("foreign")).thenReturn(new Flow("foreign", 8, now, now, 65));

        assertThatThrownBy(() -> service.anyHesitation(7, "foreign", List.of())).isInstanceOf(BusinessException.class);
    }

    private void givenFlow(String flowId, long customerId, Integer activeSeconds, String... eventTypes) {
        when(flows.find(flowId)).thenReturn(new Flow(flowId, customerId, now, now, activeSeconds));
        java.util.ArrayList<Event> events = new java.util.ArrayList<>();
        events.add(event(1, "TRANSFER_ENTER"));
        for (int index = 0; index < eventTypes.length; index++) {
            events.add(event(index + 2, eventTypes[index]));
        }
        events.add(event(eventTypes.length + 2, "SUBMIT_REQUESTED"));
        when(flows.events(flowId)).thenReturn(events);
    }

    private Event event(int sequence, String type) {
        return new Event(sequence, type, now.plusSeconds(sequence), "D-01");
    }
}
