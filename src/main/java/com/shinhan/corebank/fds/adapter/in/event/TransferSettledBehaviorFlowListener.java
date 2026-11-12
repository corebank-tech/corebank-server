package com.shinhan.corebank.fds.adapter.in.event;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferSettled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 성공 이체 커밋 뒤 refId와 검증 행동 흐름을 P6 매핑에 멱등 연결한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferSettledBehaviorFlowListener {

    private final BehaviorFlowMappingUseCase mappings;

    @ApplicationModuleListener
    public void on(TransferSettled event) {
        if (event.status() != ProcessResultStatus.SUCCESS || event.flowId() == null) {
            return;
        }
        try {
            mappings.save(event.refId(), event.flowId(), event.customerId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != FdsErrorCode.NOT_FOUND
                    && exception.getErrorCode() != FdsErrorCode.FLOW_CONFLICT) {
                throw exception;
            }
            // 잘못된 소유권·충돌은 재시도로 회복되지 않으므로 기존 매핑을 보존하고 발행을 마감한다.
            log.warn(
                    "FDS 행동 흐름 연결 거부: refId={}, code={}",
                    event.refId(),
                    exception.getErrorCode().getCode());
        }
    }
}
