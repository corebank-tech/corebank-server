package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ConfirmDecisionUseCase.Command;
import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 판정 확정의 락 안 부분. 판정 상태 조건부 갱신, 해제 허용 확인, 계좌 반영, 감사를 한 트랜잭션에 묶는다. */
@Component
@RequiredArgsConstructor
class DecisionWriter {

    private static final String PURPOSE_DECISION = "DECISION";

    private final PaymentStopRecordPort recordPort;
    private final AccountStopPort accountStopPort;
    private final GateEvaluator gateEvaluator;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public ObjectionDecision write(Command command, AdminActor actor, GateResult gate) {
        // 동시 확정은 이 행 락에서 줄을 서고, 뒤에 온 요청은 DECIDED 를 보고 PST0302 로 끝난다
        Objection objection = recordPort
                .findObjectionForUpdate(command.objectionId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        objection.decide();

        if (!gate.allowsRelease(command.result())) {
            throw new BusinessException(PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
        }

        PaymentStopCase paymentStopCase = recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));
        long accountId = paymentStopCase.getAccountId();
        long caseId = paymentStopCase.getCaseId();

        Long requestedHold = null;
        Long appliedHold = null;
        switch (command.result()) {
            case ACCEPTED -> accountStopPort.release(accountId, caseId);
            case PARTIAL -> {
                // 사유 3호 수용: 피해금(다툼 입금액 합)만 남기고 푼다(D28)
                requestedHold = paymentStopCase.disputedTotal();
                appliedHold = accountStopPort.convertToPartial(accountId, caseId, requestedHold);
            }
            case REJECTED -> {
                // 지급정지 유지
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ObjectionDecision decision = new ObjectionDecision(
                command.objectionId(),
                command.result(),
                command.reason(),
                command.earlyReleaseBasis(),
                requestedHold,
                appliedHold,
                actor.adminCustomerId(),
                now);

        recordPort.saveDecision(decision);
        recordPort.updateObjectionStatus(objection);
        paymentStopCase.reflect(command.result());
        recordPort.updateCaseStatus(paymentStopCase);
        recordPort.saveGateEvaluation(command.objectionId(), PURPOSE_DECISION, gate, gateEvaluator.settings(), now);

        Map<String, Object> detail = new HashMap<>();
        detail.put("objectionId", command.objectionId());
        detail.put("caseId", caseId);
        detail.put("accountId", accountId);
        detail.put("result", command.result().name());
        detail.put("gateBranch", gate.branch().name());
        detail.put("rulesVersion", gate.rulesVersion());
        auditLogService.record(
                actor.adminCustomerId(),
                null,
                AuditEventType.OBJECTION_DECISION_CONFIRM,
                actor.requestIp(),
                true,
                detail);

        if (command.result() != DecisionResult.REJECTED) {
            Map<String, Object> releaseDetail = new HashMap<>(detail);
            if (appliedHold != null) {
                releaseDetail.put("requestedHoldAmount", requestedHold);
                releaseDetail.put("appliedHoldAmount", appliedHold);
            }
            auditLogService.record(
                    actor.adminCustomerId(),
                    null,
                    AuditEventType.PAYMENT_STOP_RELEASE,
                    actor.requestIp(),
                    true,
                    releaseDetail);
        }

        return decision;
    }
}
