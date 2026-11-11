package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.fds.api.BehaviorFlowMapping;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowMappingPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 성공 이체 refId와 고객 소유 행동 흐름을 멱등하게 연결한다. */
@Service
@RequiredArgsConstructor
public class BehaviorFlowMappingService implements BehaviorFlowMappingUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BehaviorFlowPort flows;
    private final BehaviorFlowMappingPort mappings;
    private final Clock clock;

    @Override
    @Transactional
    public BehaviorFlowMapping save(long refId, String flowId, long customerId) {
        if (refId <= 0 || customerId <= 0 || !isUuid(flowId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        BehaviorFlowPort.Flow flow = flows.find(flowId);
        // 미존재와 타인 소유를 같은 오류로 처리해 flow 존재 여부를 노출하지 않는다.
        if (flow == null || flow.customerId() != customerId) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND);
        }

        mappings.insertOrKeepExisting(refId, flowId, customerId, LocalDateTime.now(clock.withZone(KST)));
        BehaviorFlowMappingPort.Mapping stored = mappings.findByRefIdForUpdate(refId);
        if (stored == null || !stored.flowId().equals(flowId) || stored.customerId() != customerId) {
            throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        }
        return view(stored);
    }

    @Override
    @Transactional(readOnly = true)
    public BehaviorFlowMapping findByRefId(long refId) {
        if (refId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return viewOrNull(mappings.findByRefId(refId));
    }

    @Override
    @Transactional(readOnly = true)
    public BehaviorFlowMapping findByFlowId(String flowId) {
        if (!isUuid(flowId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return viewOrNull(mappings.findByFlowId(flowId));
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static BehaviorFlowMapping viewOrNull(BehaviorFlowMappingPort.Mapping mapping) {
        return mapping == null ? null : view(mapping);
    }

    private static BehaviorFlowMapping view(BehaviorFlowMappingPort.Mapping mapping) {
        return new BehaviorFlowMapping(mapping.refId(), mapping.flowId(), mapping.customerId());
    }
}
