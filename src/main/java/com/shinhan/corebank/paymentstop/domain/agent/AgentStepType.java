package com.shinhan.corebank.paymentstop.domain.agent;

// 단계 종류. docs/phase2/agent_run_step_schema.md §4 와 같다.
public enum AgentStepType {
    CLAIMED,
    CONTEXT_LOADED,
    OCR,
    TOOL_CALL,
    CLAIM_EXTRACTED,
    CONTRADICTION,
    DRAFT_SUBMITTED,
    // 서버만 남긴다. Agent 가 보내면 거부한다
    GATE_EVALUATED,
    FAILED
}
