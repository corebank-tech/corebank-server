package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;

public interface ApproveDecisionUseCase {

    /**
     * 승인 대기 판정을 승인해 확정한다. 승인자는 작성자와 달라야 하고(PST0309), 재량 판정이면 승인자가 재량임을 확인해야 한다(PST0311).
     * 게이트를 락 밖에서 다시 계산한 뒤(D11) 락 안에서 해제 허용을 보고 account.api 로 계좌에 반영한다. 재량 판정은 그사이 재량으로도
     * 넘지 못하는 사유가 생겼으면 PST0310 이다. 동시 승인은 하나만 확정된다.
     */
    ObjectionDecision approve(long objectionId, long decisionId, boolean discretionaryAcknowledged, AdminActor actor);

    /** 재량 확인 없이 승인한다. 일반 판정은 이전처럼 승인되고 재량 판정은 PST0311 이다. */
    default ObjectionDecision approve(long objectionId, long decisionId, AdminActor actor) {
        return approve(objectionId, decisionId, false, actor);
    }

    /** 승인 대기 판정을 반려한다. 판정은 RETURNED, 이의제기는 심사 중으로 돌아간다. */
    ObjectionDecision returnDecision(long objectionId, long decisionId, String reason, AdminActor actor);
}
