package com.shinhan.corebank.fds.application.port.out;

import java.time.LocalDateTime;

/** refId-flowId 매핑을 DB 유니크 제약으로 저장하는 출력 포트. */
public interface BehaviorFlowMappingPort {

    void insertOrKeepExisting(long refId, String flowId, long customerId, LocalDateTime createdAt);

    Mapping findByRefId(long refId);

    Mapping findByRefIdForUpdate(long refId);

    Mapping findByFlowId(String flowId);

    record Mapping(long refId, String flowId, long customerId) {}
}
