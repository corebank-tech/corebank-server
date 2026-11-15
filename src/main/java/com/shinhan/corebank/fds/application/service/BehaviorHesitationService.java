package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorFlowMapping;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** P6에 검증·확정된 행동 흐름만 읽어 반복 묶음의 망설임을 계산한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BehaviorHesitationService {

    private final BehaviorFlowPort flows;
    private final BehaviorFlowMappingUseCase mappings;

    public boolean anyHesitation(long customerId, String currentFlowId, List<Long> previousRefIds) {
        if (currentFlowId != null && isHesitantOwnedFlow(currentFlowId, customerId, true)) {
            return true;
        }
        for (Long refId : previousRefIds) {
            BehaviorFlowMapping mapping = mappings.findByRefId(refId);
            if (mapping != null
                    && mapping.customerId() == customerId
                    && isHesitantOwnedFlow(mapping.flowId(), customerId, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHesitantOwnedFlow(String flowId, long customerId, boolean rejectWrongOwner) {
        Flow flow = flows.find(flowId);
        if (flow == null) {
            return false;
        }
        if (flow.customerId() != customerId) {
            if (rejectWrongOwner) {
                throw new BusinessException(FdsErrorCode.NOT_FOUND);
            }
            return false;
        }
        if (flow.finalizedAt() == null) {
            return false;
        }
        BehaviorFeatureExtractor.Features features = BehaviorFeatureExtractor.extract(flows.events(flowId), flow);
        if (features == null) return false;
        int confirmedSignals = 0;
        if (features.backCount() >= 1) confirmedSignals++;
        if (features.amountRevisionCount() >= 2) confirmedSignals++;
        if (features.confirmActiveSeconds() != null && features.confirmActiveSeconds() >= 60) confirmedSignals++;
        return confirmedSignals >= 2;
    }
}
