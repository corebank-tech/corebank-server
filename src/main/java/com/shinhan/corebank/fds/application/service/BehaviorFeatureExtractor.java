package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Event;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 점수 추론과 정책 판정이 같은 검증 행동 피처를 사용하게 한다. */
final class BehaviorFeatureExtractor {

    private BehaviorFeatureExtractor() {}

    static Features extract(List<Event> events, Flow flow) {
        if (events.isEmpty()
                || !"TRANSFER_ENTER".equals(events.getFirst().eventType())
                || !"SUBMIT_REQUESTED".equals(events.getLast().eventType())) {
            return null;
        }
        LocalDateTime entered = events.getFirst().occurredAt();
        LocalDateTime submitted = events.getLast().occurredAt();
        long total = Duration.between(entered, submitted).toSeconds();
        if (total < 0 || total > Integer.MAX_VALUE) {
            return null;
        }
        int back = 0;
        int revisions = 0;
        Integer recipient = null;
        for (Event event : events) {
            if ("STEP_BACK".equals(event.eventType())) back++;
            if ("AMOUNT_EDITED".equals(event.eventType())) revisions++;
            if ("RECIPIENT_CONFIRMED".equals(event.eventType()) && recipient == null) {
                long seconds = Duration.between(entered, event.occurredAt()).toSeconds();
                if (seconds < 0 || seconds > total) return null;
                recipient = (int) seconds;
            }
        }
        if (back > 100 || revisions > 100) return null;
        return new Features(back, revisions, flow.confirmActiveSeconds(), recipient, (int) total);
    }

    record Features(
            int backCount,
            int amountRevisionCount,
            Integer confirmActiveSeconds,
            Integer recipientConfirmSeconds,
            int totalFlowSeconds) {}
}
