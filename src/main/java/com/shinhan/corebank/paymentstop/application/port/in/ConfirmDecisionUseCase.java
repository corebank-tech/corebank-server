package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;

public interface ConfirmDecisionUseCase {

    /**
     * 판정을 확정한다(1인 확정, 2인 승인은 PA-10). 게이트를 계좌 락 밖에서 다시 계산하고, 락 안에서 판정 상태와 해제 허용을 본 뒤
     * account.api 로 계좌에 반영한다(D11).
     */
    ObjectionDecision confirm(Command command, AdminActor actor);

    record Command(long objectionId, DecisionResult result, String reason, String earlyReleaseBasis) {}
}
