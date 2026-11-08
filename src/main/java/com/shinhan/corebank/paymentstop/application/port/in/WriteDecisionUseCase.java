package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;

public interface WriteDecisionUseCase {

    /**
     * 판정을 쓴다(D13). 불수용은 바로 확정하고, 수용과 일부 지급정지는 계좌를 건드리지 않고 승인 대기로 저장한다. 해제 게이트가 열지 않는
     * 판정은 쓰지 않는다(PST0301). 다만 재량 판정이면 재량으로도 넘지 못하는 사유(PST0310)가 없을 때 쓴다(ADR-0011). 게이트가 이미 연
     * 판정에 재량 표시를 하면 입력 오류(CMN0001)다. 게이트는 락 밖에서 계산한다(D11).
     */
    ObjectionDecision write(Command command, AdminActor actor);

    /**
     * @param discretionary 담당자 재량 판정인지(ADR-0011)
     * @param discretionaryReason 재량 사유. 재량이면 20~1000자 필수, 아니면 버린다
     */
    record Command(
            long objectionId,
            DecisionResult result,
            String reason,
            String earlyReleaseBasis,
            boolean discretionary,
            String discretionaryReason) {

        public Command(long objectionId, DecisionResult result, String reason, String earlyReleaseBasis) {
            this(objectionId, result, reason, earlyReleaseBasis, false, null);
        }
    }
}
