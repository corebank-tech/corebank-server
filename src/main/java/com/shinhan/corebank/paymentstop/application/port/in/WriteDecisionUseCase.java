package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;

public interface WriteDecisionUseCase {

    /**
     * 판정을 쓴다(D13). 불수용은 바로 확정하고, 수용과 일부 지급정지는 계좌를 건드리지 않고 승인 대기로 저장한다. 해제 게이트가 열지 않는
     * 판정은 쓰지 않는다. 게이트는 락 밖에서 계산한다(D11).
     */
    ObjectionDecision write(Command command, AdminActor actor);

    record Command(long objectionId, DecisionResult result, String reason, String earlyReleaseBasis) {}
}
