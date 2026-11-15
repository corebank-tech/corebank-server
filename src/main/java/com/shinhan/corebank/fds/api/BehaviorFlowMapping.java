package com.shinhan.corebank.fds.api;

/** 성공 이체와 FE 행동 흐름을 잇는 P6 소유 매핑. */
public record BehaviorFlowMapping(long refId, String flowId, long customerId) {}
