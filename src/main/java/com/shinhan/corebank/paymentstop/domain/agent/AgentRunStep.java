package com.shinhan.corebank.paymentstop.domain.agent;

import java.time.LocalDateTime;
import java.util.Map;

/** 실행 안의 단계 하나. 텍스트는 가명 처리한 값만 담는다. */
public record AgentRunStep(
        long runId,
        int seq,
        AgentStepType type,
        String title,
        String summary,
        Map<String, Object> detail,
        AgentStepStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt) {

    public AgentRunStep {
        if (seq < 1) {
            throw new IllegalArgumentException("단계 순번은 1부터입니다.");
        }
        if (type == null || status == null || title == null || title.isBlank() || startedAt == null) {
            throw new IllegalArgumentException("단계 종류, 상태, 제목, 시작 시각은 필수입니다.");
        }
    }
}
