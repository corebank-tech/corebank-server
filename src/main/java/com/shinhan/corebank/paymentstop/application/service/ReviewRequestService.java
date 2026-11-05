package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.RequestReviewUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewRequestService implements RequestReviewUseCase {

    private final PaymentStopRecordPort recordPort;
    private final AgentRunPort agentRunPort;
    private final ReviewJobEnqueuer reviewJobEnqueuer;

    @Override
    @Transactional
    public long request(long objectionId, AdminActor actor) {
        // 동시 재분석 요청이 둘 다 진행 중 실행 없음을 보고 둘 다 넣지 않도록 이의제기 행을 잠근다
        Objection objection = recordPort
                .findObjectionForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        if (objection.getStatus() != ObjectionStatus.RECEIVED) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
        }
        if (agentRunPort.findLatestRun(objectionId).filter(AgentRun::isActive).isPresent()) {
            throw new BusinessException(PaymentStopErrorCode.AGENT_RUN_IN_PROGRESS);
        }
        return reviewJobEnqueuer.enqueue(objectionId, actor.adminCustomerId());
    }
}
