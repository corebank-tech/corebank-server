package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ApproveDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.WriteDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 판정 작성과 2인 승인(D13). 게이트는 트랜잭션과 락 밖에서 계산하고(180일 원장 조회 중 락을 쥐지 않으려는 것), 기록 반영은
 * {@link DecisionWriter}가 이의제기 행 락 안에서 한다(D11). 전부 지급정지 중에는 입출금이 막혀 원장이 바뀌지 않으므로 락 밖 계산이 안전하다.
 *
 * <p>여기서 하는 상태와 승인자 확인은 빠른 거절용이다. 같은 확인을 락 안에서 다시 한다.
 *
 * <p>재량 판정(ADR-0011): 게이트가 닫은 수용과 일부 지급정지는 재량 표시와 사유가 있고 재량으로도 넘지 못하는 사유가 없을 때만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class DecisionService implements WriteDecisionUseCase, ApproveDecisionUseCase {

    private final PaymentStopRecordPort recordPort;
    private final GateEvaluator gateEvaluator;
    private final DecisionWriter decisionWriter;

    @Override
    public ObjectionDecision write(WriteDecisionUseCase.Command command, AdminActor actor) {
        // 계좌를 건드리기 전에 막을 수 있는 것은 먼저 막는다
        ObjectionDecision.requireBasis(command.result(), command.earlyReleaseBasis());
        ObjectionDecision.requireDiscretionaryReason(
                command.result(), command.discretionary(), command.discretionaryReason());

        Objection objection = objection(command.objectionId());
        objection.requireDecidable(command.result());

        // 작성 때도 게이트를 본다. 열리지 않는 판정은 재량이 아니면 승인 대기로도 올리지 않는다
        GateResult gate = gateEvaluator.evaluate(caseOf(objection), objection);
        if (gate.allowsRelease(command.result())) {
            if (command.discretionary()) {
                // 게이트가 연 판정에 재량 표시를 하면 재량 기록이 부풀어 감사 의미가 흐려진다
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        } else if (!command.discretionary()) {
            throw new BusinessException(PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
        } else if (!gate.allowsDiscretionary(command.result())) {
            throw new BusinessException(PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);
        }

        return decisionWriter.write(command, actor, gate);
    }

    @Override
    public ObjectionDecision approve(
            long objectionId, long decisionId, boolean discretionaryAcknowledged, AdminActor actor) {
        Objection objection = objection(objectionId);
        objection.requireNotDecided();
        ObjectionDecision pending = pendingDecision(objectionId, decisionId);
        pending.requireApprover(actor.adminCustomerId());
        pending.requireDiscretionaryAcknowledged(discretionaryAcknowledged);

        // 확정 직전 게이트 재계산(D11). 분석 이후 외부 확인 회신이 들어왔으면 여기서 반영된다
        GateResult gate = gateEvaluator.evaluate(caseOf(objection), objection);

        return decisionWriter.approve(objectionId, decisionId, discretionaryAcknowledged, actor, gate);
    }

    @Override
    public ObjectionDecision returnDecision(long objectionId, long decisionId, String reason, AdminActor actor) {
        return decisionWriter.returnDecision(objectionId, decisionId, reason, actor);
    }

    private ObjectionDecision pendingDecision(long objectionId, long decisionId) {
        return recordPort
                .findDecisionById(decisionId)
                .filter(decision -> decision.objectionId() == objectionId && decision.pendingApproval())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND));
    }

    private Objection objection(long objectionId) {
        return recordPort
                .findObjection(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
    }

    private PaymentStopCase caseOf(Objection objection) {
        return recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));
    }
}
