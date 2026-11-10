package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Event;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객별 행동 이벤트를 순서대로 검증하고 한 트랜잭션에 저장한다. */
@Service
@RequiredArgsConstructor
public class BehaviorFlowService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> TYPES = Set.of(
            "TRANSFER_ENTER",
            "RECIPIENT_CONFIRMED",
            "AMOUNT_EDITED",
            "STEP_BACK",
            "CONFIRM_ENTER",
            "CONFIRM_LEAVE",
            "SUBMIT_REQUESTED");

    private final BehaviorFlowPort port;
    private final Clock clock;

    public record EventInput(UUID flowId, int seqNo, String eventType, OffsetDateTime occurredAt, String step) {}

    public record Batch(List<EventInput> events, Integer confirmActiveSeconds) {}

    public record Accepted(UUID flowId, int acceptedCount) {}

    @Transactional
    public Accepted submit(UUID flowId, long customerId, Batch batch) {
        if (flowId == null
                || customerId <= 0
                || batch == null
                || batch.events() == null
                || batch.events().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        LocalDateTime receivedAt = LocalDateTime.now(clock.withZone(KST));
        port.create(flowId.toString(), customerId, receivedAt);
        port.lock(flowId.toString());
        Flow flow = port.find(flowId.toString());
        if (flow.customerId() != customerId) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (Duration.between(flow.firstReceivedAt(), receivedAt).toMinutes() >= 30) {
            throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        }
        List<Event> existing = port.events(flowId.toString());
        if (existing.size() + batch.events().size() > 200) {
            throw new BusinessException(FdsErrorCode.INVALID_EVENT);
        }
        int next = existing.size() + 1;
        LocalDateTime previousAt =
                existing.isEmpty() ? null : existing.getLast().occurredAt();
        boolean confirmOpen = false;
        for (Event stored : existing) {
            if ("CONFIRM_ENTER".equals(stored.eventType())) confirmOpen = true;
            if ("CONFIRM_LEAVE".equals(stored.eventType())) confirmOpen = false;
        }
        Set<Integer> seen = new HashSet<>();
        int appended = 0;
        boolean finalEvent = false;
        for (EventInput input : batch.events()) {
            if (input == null
                    || input.flowId() == null
                    || !input.flowId().equals(flowId)
                    || input.seqNo() < 1
                    || input.seqNo() > 100
                    || !seen.add(input.seqNo())
                    || !TYPES.contains(input.eventType())
                    || input.occurredAt() == null
                    || !Set.of("D-01", "D-02").contains(input.step())) {
                throw new BusinessException(FdsErrorCode.INVALID_EVENT);
            }
            LocalDateTime occurredAt = input.occurredAt().atZoneSameInstant(KST).toLocalDateTime();
            // MySQL DATETIME(6) 정밀도로 맞춰 동일 이벤트 재전송을 정확히 비교한다.
            occurredAt = occurredAt.withNano(occurredAt.getNano() / 1_000 * 1_000);
            if (occurredAt.isBefore(receivedAt.minusMinutes(30)) || occurredAt.isAfter(receivedAt.plusMinutes(5))) {
                throw new BusinessException(FdsErrorCode.INVALID_EVENT);
            }
            Event current = new Event(input.seqNo(), input.eventType(), occurredAt, input.step());
            if (input.seqNo() <= existing.size()) {
                if (!existing.get(input.seqNo() - 1).equals(current)) {
                    throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
                }
                continue;
            }
            if (flow.finalizedAt() != null
                    || input.seqNo() != next
                    || previousAt != null && occurredAt.isBefore(previousAt)) {
                throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
            }
            if (next == 1 && !"TRANSFER_ENTER".equals(input.eventType())
                    || finalEvent
                    || "SUBMIT_REQUESTED".equals(input.eventType()) && !"D-02".equals(input.step())) {
                throw new BusinessException(FdsErrorCode.INVALID_EVENT);
            }
            if (("TRANSFER_ENTER".equals(input.eventType())
                                    || "RECIPIENT_CONFIRMED".equals(input.eventType())
                                    || "AMOUNT_EDITED".equals(input.eventType())
                                    || "STEP_BACK".equals(input.eventType()))
                            && !"D-01".equals(input.step())
                    || ("CONFIRM_ENTER".equals(input.eventType()) || "CONFIRM_LEAVE".equals(input.eventType()))
                            && !"D-02".equals(input.step())
                    || "CONFIRM_ENTER".equals(input.eventType()) && confirmOpen
                    || "CONFIRM_LEAVE".equals(input.eventType()) && !confirmOpen
                    || "SUBMIT_REQUESTED".equals(input.eventType()) && confirmOpen) {
                throw new BusinessException(FdsErrorCode.INVALID_EVENT);
            }
            if ("CONFIRM_ENTER".equals(input.eventType())) confirmOpen = true;
            if ("CONFIRM_LEAVE".equals(input.eventType())) confirmOpen = false;
            finalEvent = "SUBMIT_REQUESTED".equals(input.eventType());
            port.append(flowId.toString(), current, receivedAt);
            previousAt = occurredAt;
            next++;
            appended++;
        }
        if (next > 101
                || batch.confirmActiveSeconds() != null
                        && (batch.confirmActiveSeconds() < 0 || batch.confirmActiveSeconds() > 1800)) {
            throw new BusinessException(FdsErrorCode.INVALID_EVENT);
        }
        if (batch.confirmActiveSeconds() != null && !finalEvent && flow.finalizedAt() == null) {
            throw new BusinessException(FdsErrorCode.INVALID_EVENT);
        }
        if (flow.finalizedAt() != null
                && appended == 0
                && !java.util.Objects.equals(flow.confirmActiveSeconds(), batch.confirmActiveSeconds())) {
            throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        }
        if (finalEvent) {
            port.finalizeFlow(flowId.toString(), batch.confirmActiveSeconds(), receivedAt);
        }
        return new Accepted(flowId, appended);
    }
}
