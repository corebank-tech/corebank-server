package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsAdminActionStore.Action;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** P6 트랜잭션 밖에서 P4를 호출하고 저장된 의도와 최종 결과를 대조한다. */
@Service
@RequiredArgsConstructor
public class FdsAdminActionService {
    private final FdsAdminActionStore store;
    private final TransferHoldUseCase holds;

    public Action act(long holdId, String idempotencyKey, long actorId, String type, String reasonCode) {
        Action action = store.prepare(holdId, idempotencyKey, actorId, type, reasonCode);
        // 충돌로 확정된 멱등 재요청도 성공 응답으로 바꾸지 않고 최초 요청과 같은 오류를 반환한다.
        if ("CONFLICT".equals(action.status())) throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        if (!"PENDING".equals(action.status())) return action;
        RuntimeException invocationFailure = null;
        try {
            invokeIfNeeded(action);
        } catch (RuntimeException exception) {
            invocationFailure = exception;
        }
        // P4 응답 유실과 상태 전이 후 예외를 구분하려면 반드시 정본 상태를 다시 읽는다.
        TransferHoldForFds actual = holds.findForFds(holdId);
        Action result = store.reconcile(action, actual);
        if ("CONFLICT".equals(result.status())
                || ("EXPIRED".equals(actual.status()) && "APPROVE".equals(action.actionType()))) {
            throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        }
        if (invocationFailure != null && !"COMPLETED".equals(result.status())) throw invocationFailure;
        return result;
    }

    public void recoverPending() {
        for (Action action : store.pending()) {
            try {
                invokeIfNeeded(action);
                store.reconcile(action, holds.findForFds(action.holdId()));
            } catch (RuntimeException ignored) {
                // 다음 주기에도 PENDING 의도와 P4 정본을 다시 대조한다.
            }
        }
    }

    private void invokeIfNeeded(Action action) {
        TransferHoldForFds current = holds.findForFds(action.holdId());
        if (!"HELD".equals(current.status())) return;
        if ("APPROVE".equals(action.actionType())) {
            holds.release(action.holdId(), action.actorId());
        } else {
            holds.reject(action.holdId(), action.actorId(), action.reasonCode());
        }
    }
}
