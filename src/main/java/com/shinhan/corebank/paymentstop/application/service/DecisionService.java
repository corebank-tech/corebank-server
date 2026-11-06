package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ConfirmDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 판정 확정. 게이트는 트랜잭션과 락 밖에서 계산하고(180일 원장 조회 중 락을 쥐지 않으려는 것), 기록 반영은 {@link DecisionWriter}가
 * 한 트랜잭션에서 한다(D11). 전부 지급정지 중에는 입출금이 막혀 원장이 바뀌지 않으므로 락 밖 계산이 안전하다.
 */
@Service
@RequiredArgsConstructor
public class DecisionService implements ConfirmDecisionUseCase {

    private final PaymentStopRecordPort recordPort;
    private final GateEvaluator gateEvaluator;
    private final DecisionWriter decisionWriter;

    @Override
    public ObjectionDecision confirm(Command command, AdminActor actor) {
        // 계좌를 건드리기 전에 막을 수 있는 것은 먼저 막는다
        ObjectionDecision.requireBasis(command.result(), command.earlyReleaseBasis());

        Objection objection = recordPort
                .findObjection(command.objectionId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        if (objection.getStatus() != ObjectionStatus.RECEIVED) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
        }
        PaymentStopCase paymentStopCase = recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));

        GateResult gate = gateEvaluator.evaluate(paymentStopCase, objection);

        return decisionWriter.write(command, actor, gate);
    }
}
