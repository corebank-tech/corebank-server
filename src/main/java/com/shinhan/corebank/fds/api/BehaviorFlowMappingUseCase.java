package com.shinhan.corebank.fds.api;

/** 후속 성공 이체 리스너와 정책 평가가 사용하는 P6 공개 계약. */
public interface BehaviorFlowMappingUseCase {

    BehaviorFlowMapping save(long refId, String flowId, long customerId);

    BehaviorFlowMapping findByRefId(long refId);

    BehaviorFlowMapping findByFlowId(String flowId);
}
