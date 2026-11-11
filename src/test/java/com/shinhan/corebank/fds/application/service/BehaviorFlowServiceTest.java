package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Event;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 이벤트 소유권·순번·마감 검증을 메모리 저장소로 확인한다. */
class BehaviorFlowServiceTest {
    private final UUID flowId = UUID.fromString("3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T05:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MemoryFlowPort port = new MemoryFlowPort();
    private final BehaviorFlowService service = new BehaviorFlowService(port, clock);

    @Test
    void storesFinalBatchOnceAndPreservesMissingActiveSeconds() {
        var batch = new BehaviorFlowService.Batch(
                List.of(
                        event(1, "TRANSFER_ENTER", -30, "D-01"),
                        event(2, "RECIPIENT_CONFIRMED", -15, "D-01"),
                        event(3, "SUBMIT_REQUESTED", -1, "D-02")),
                null);

        assertThat(service.submit(flowId, 7, batch).acceptedCount()).isEqualTo(3);
        assertThat(service.submit(flowId, 7, batch).acceptedCount()).isZero();
        assertThat(port.events).hasSize(3);
        assertThat(port.flow.finalizedAt()).isNotNull();
        assertThat(port.flow.confirmActiveSeconds()).isNull();
    }

    @Test
    void refusesDifferentOwnerAndConflictingReplay() {
        var first = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -30, "D-01")), null);
        service.submit(flowId, 7, first);
        assertThatThrownBy(() -> service.submit(flowId, 8, first)).isInstanceOf(BusinessException.class);
        var conflict = new BehaviorFlowService.Batch(List.of(event(1, "STEP_BACK", -30, "D-01")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, conflict)).isInstanceOf(BusinessException.class);
    }

    @Test
    void refusesMissingSequenceAndOutOfWindowEvent() {
        var missing = new BehaviorFlowService.Batch(List.of(event(2, "TRANSFER_ENTER", -30, "D-01")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, missing)).isInstanceOf(BusinessException.class);
        var tooOld = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -1801, "D-01")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, tooOld)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNullEventTypeAndStepAsInvalidEvent() {
        var nullType = new BehaviorFlowService.Batch(List.of(event(1, null, -1, "D-01")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, nullType))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.INVALID_EVENT);

        port.flow = null;
        var nullStep = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -1, null)), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, nullStep))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.INVALID_EVENT);
    }

    @Test
    void rejectsExpiredFlowAndUnexpectedFinalization() {
        port.flow = new Flow(flowId.toString(), 7, LocalDateTime.now(clock).minusMinutes(31), null, null);
        var first = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -1, "D-01")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, first)).isInstanceOf(BusinessException.class);

        port.flow = null;
        var prematureSeconds = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -1, "D-01")), 10);
        assertThatThrownBy(() -> service.submit(flowId, 7, prematureSeconds)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnpairedConfirmAndDifferentFinalReplay() {
        var unpaired = new BehaviorFlowService.Batch(
                List.of(event(1, "TRANSFER_ENTER", -3, "D-01"), event(2, "CONFIRM_LEAVE", -2, "D-02")), null);
        assertThatThrownBy(() -> service.submit(flowId, 7, unpaired)).isInstanceOf(BusinessException.class);

        port.flow = null;
        port.events.clear();
        var finalized = new BehaviorFlowService.Batch(
                List.of(event(1, "TRANSFER_ENTER", -3, "D-01"), event(2, "SUBMIT_REQUESTED", -1, "D-02")), 4);
        service.submit(flowId, 7, finalized);
        var changedSeconds = new BehaviorFlowService.Batch(finalized.events(), 5);
        assertThatThrownBy(() -> service.submit(flowId, 7, changedSeconds)).isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesWholeBatchBeforeAppendingAndLimitsRequestToOneHundred() {
        var invalidTail = new BehaviorFlowService.Batch(
                List.of(event(1, "TRANSFER_ENTER", -3, "D-01"), event(2, "CONFIRM_LEAVE", -2, "D-02")), null);

        assertThatThrownBy(() -> service.submit(flowId, 7, invalidTail)).isInstanceOf(BusinessException.class);
        assertThat(port.events).isEmpty();

        port.flow = null;
        var tooMany = new ArrayList<BehaviorFlowService.EventInput>();
        for (int sequence = 1; sequence <= 101; sequence++) {
            tooMany.add(event(sequence, sequence == 1 ? "TRANSFER_ENTER" : "AMOUNT_EDITED", -1, "D-01"));
        }
        assertThatThrownBy(() -> service.submit(flowId, 7, new BehaviorFlowService.Batch(tooMany, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(port.events).isEmpty();
    }

    @Test
    void acceptsAllSevenTypesAndCountsOnlyNewEventsInMixedReplay() {
        var first = new BehaviorFlowService.Batch(
                List.of(
                        event(1, "TRANSFER_ENTER", -10, "D-01"),
                        event(2, "RECIPIENT_CONFIRMED", -9, "D-01"),
                        event(3, "AMOUNT_EDITED", -8, "D-01"),
                        event(4, "STEP_BACK", -7, "D-01")),
                null);
        service.submit(flowId, 7, first);

        var mixed = new BehaviorFlowService.Batch(
                List.of(
                        event(4, "STEP_BACK", -7, "D-01"),
                        event(5, "CONFIRM_ENTER", -6, "D-02"),
                        event(6, "CONFIRM_LEAVE", -2, "D-02"),
                        event(7, "SUBMIT_REQUESTED", -1, "D-02")),
                4);

        assertThat(service.submit(flowId, 7, mixed).acceptedCount()).isEqualTo(3);
        assertThat(port.events).hasSize(7);
    }

    @Test
    void finalizedFlowAllowsPureIdenticalEventReplayWithoutActiveSeconds() {
        var finalBatch = new BehaviorFlowService.Batch(
                List.of(event(1, "TRANSFER_ENTER", -3, "D-01"), event(2, "SUBMIT_REQUESTED", -1, "D-02")), 2);
        service.submit(flowId, 7, finalBatch);

        var earlierReplay = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -3, "D-01")), null);

        assertThat(service.submit(flowId, 7, earlierReplay).acceptedCount()).isZero();
        assertThat(port.events).hasSize(2);
    }

    @Test
    void rejectsReplayedEventsInReverseSequenceOrder() {
        var stored = new BehaviorFlowService.Batch(
                List.of(
                        event(1, "TRANSFER_ENTER", -4, "D-01"),
                        event(2, "RECIPIENT_CONFIRMED", -3, "D-01"),
                        event(3, "AMOUNT_EDITED", -2, "D-01"),
                        event(4, "STEP_BACK", -1, "D-01")),
                null);
        service.submit(flowId, 7, stored);
        var reversed = new BehaviorFlowService.Batch(
                List.of(event(4, "STEP_BACK", -1, "D-01"), event(3, "AMOUNT_EDITED", -2, "D-01")), null);

        assertThatThrownBy(() -> service.submit(flowId, 7, reversed))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.INVALID_EVENT);
    }

    @Test
    void identicalReplayRemainsIdempotentAfterOccurrenceFallsOutsideCurrentWindow() {
        var original = new BehaviorFlowService.Batch(List.of(event(1, "TRANSFER_ENTER", -1799, "D-01")), null);
        service.submit(flowId, 7, original);
        BehaviorFlowService laterService = new BehaviorFlowService(port, Clock.offset(clock, Duration.ofSeconds(2)));

        assertThat(laterService.submit(flowId, 7, original).acceptedCount()).isZero();
        assertThat(port.events).hasSize(1);
    }

    private BehaviorFlowService.EventInput event(int seq, String type, long seconds, String step) {
        return new BehaviorFlowService.EventInput(
                flowId,
                seq,
                type,
                OffsetDateTime.ofInstant(clock.instant().plusSeconds(seconds), ZoneId.of("Asia/Seoul")),
                step);
    }

    private static class MemoryFlowPort implements BehaviorFlowPort {
        Flow flow;
        List<Event> events = new ArrayList<>();

        @Override
        public Flow find(String id) {
            return flow;
        }

        @Override
        public void create(String id, long customer, LocalDateTime now) {
            if (flow == null) flow = new Flow(id, customer, now, null, null);
        }

        @Override
        public void lock(String id) {}

        @Override
        public void append(String id, Event event, LocalDateTime now) {
            events.add(event);
        }

        @Override
        public void finalizeFlow(String id, Integer seconds, LocalDateTime now) {
            flow = new Flow(id, flow.customerId(), flow.firstReceivedAt(), now, seconds);
        }

        @Override
        public List<Event> events(String id) {
            return List.copyOf(events);
        }
    }
}
