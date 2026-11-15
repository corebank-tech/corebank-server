package com.shinhan.corebank.fds.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/** P6 소유 행동 이벤트 저장·조회 계약. */
public interface BehaviorFlowPort {

    Flow find(String flowId);

    void create(String flowId, long customerId, LocalDateTime receivedAt);

    void lock(String flowId);

    void append(String flowId, Event event, LocalDateTime receivedAt);

    void finalizeFlow(String flowId, Integer confirmActiveSeconds, LocalDateTime receivedAt);

    List<Event> events(String flowId);

    record Flow(
            String flowId,
            long customerId,
            LocalDateTime firstReceivedAt,
            LocalDateTime finalizedAt,
            Integer confirmActiveSeconds) {}

    record Event(int seqNo, String eventType, LocalDateTime occurredAt, String step) {}
}
