package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import java.util.List;
import java.util.Optional;

public interface ObjectionQueryUseCase {

    List<Summary> list();

    /** 상세는 소명 원문이 보이므로 조회 사유를 받고 감사를 남긴다(PH-89). 게이트는 조회 시점 원장으로 다시 계산한다. */
    Detail detail(long objectionId, AdminActor actor, AccessReason accessReason);

    record Summary(Objection objection, PaymentStopCase paymentStopCase) {}

    record Detail(
            Objection objection,
            PaymentStopCase paymentStopCase,
            GateResult gate,
            Optional<ObjectionDecision> decision) {}
}
