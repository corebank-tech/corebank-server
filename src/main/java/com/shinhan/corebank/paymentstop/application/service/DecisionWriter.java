package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.WriteDecisionUseCase.Command;
import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.CaseStatus;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.notice.NoticePlan;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정의 락 안 부분. 이의제기 행을 잠그고 상태를 다시 본 뒤 판정, 계좌 반영, 게이트 기록, 감사, 통지를 한 트랜잭션에 묶는다. 동시 작성과
 * 동시 승인은 이 행 락에서 줄을 서고, 뒤에 온 요청은 바뀐 상태를 보고 거절된다.
 */
@Component
@RequiredArgsConstructor
class DecisionWriter {

    private static final String PURPOSE_DECISION = "DECISION";

    private final PaymentStopRecordPort recordPort;
    private final AccountStopPort accountStopPort;
    private final GateEvaluator gateEvaluator;
    private final NoticeRecorder noticeRecorder;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public ObjectionDecision write(Command command, AdminActor actor, GateResult gate) {
        Objection objection = lock(command.objectionId());
        objection.writeDecision(command.result());

        LocalDateTime now = LocalDateTime.now(clock);
        ObjectionDecision decision = recordPort.saveDecision(ObjectionDecision.write(
                command.objectionId(),
                command.result(),
                command.reason(),
                command.earlyReleaseBasis(),
                actor.adminCustomerId(),
                now));
        recordPort.updateObjection(objection);

        PaymentStopCase paymentStopCase = caseOf(objection);
        Map<String, Object> detail = auditDetail(decision, paymentStopCase, gate);

        if (command.result() != DecisionResult.REJECTED) {
            // 보류를 줄이는 판정은 승인 대기로만 둔다. 계좌는 승인 때 바꾼다
            auditLogService.record(
                    actor.adminCustomerId(),
                    null,
                    AuditEventType.OBJECTION_DECISION_SUBMIT,
                    actor.requestIp(),
                    true,
                    detail);
            return decision;
        }

        // 불수용은 1인 확정이다. 지급정지는 그대로 둔다
        recordPort.saveGateEvaluation(command.objectionId(), PURPOSE_DECISION, gate, gateEvaluator.settings(), now);
        auditLogService.record(
                actor.adminCustomerId(),
                null,
                AuditEventType.OBJECTION_DECISION_CONFIRM,
                actor.requestIp(),
                true,
                detail);
        noticeRecorder.record(NoticePlan.decision(command.objectionId(), DecisionResult.REJECTED, false, now));
        return decision;
    }

    @Transactional
    public ObjectionDecision approve(long objectionId, long decisionId, AdminActor actor, GateResult gate) {
        // 동시 승인은 이 락에서 줄을 서고, 뒤에 온 요청은 DECIDED 를 보고 PST0302 로 끝난다
        Objection objection = lock(objectionId);
        objection.approve();

        ObjectionDecision pending = pending(objectionId, decisionId);
        pending.requireApprover(actor.adminCustomerId());
        if (!gate.allowsRelease(pending.result())) {
            throw new BusinessException(PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
        }

        PaymentStopCase paymentStopCase = caseOf(objection);
        long accountId = paymentStopCase.getAccountId();
        long caseId = paymentStopCase.getCaseId();

        Long requestedHold = null;
        Long appliedHold = null;
        switch (pending.result()) {
            case ACCEPTED -> accountStopPort.release(accountId, caseId);
            case PARTIAL -> {
                // 사유 3호 수용: 피해금(다툼 입금액 합)만 남기고 푼다(D28)
                requestedHold = paymentStopCase.disputedTotal();
                appliedHold = accountStopPort.convertToPartial(accountId, caseId, requestedHold);
            }
            case REJECTED -> throw new IllegalStateException("불수용은 승인 대기가 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ObjectionDecision confirmed = pending.approve(actor.adminCustomerId(), now, requestedHold, appliedHold);
        recordPort.updateDecision(confirmed);
        recordPort.updateObjection(objection);
        paymentStopCase.reflect(confirmed.result());
        recordPort.updateCaseStatus(paymentStopCase);
        recordPort.saveGateEvaluation(objectionId, PURPOSE_DECISION, gate, gateEvaluator.settings(), now);

        Map<String, Object> detail = auditDetail(confirmed, paymentStopCase, gate);
        detail.put("decidedBy", confirmed.decidedBy());
        auditLogService.record(
                actor.adminCustomerId(),
                null,
                AuditEventType.OBJECTION_DECISION_CONFIRM,
                actor.requestIp(),
                true,
                detail);
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

        boolean stopTerminated = paymentStopCase.getStatus() == CaseStatus.RELEASED;
        noticeRecorder.record(NoticePlan.decision(objectionId, confirmed.result(), stopTerminated, now));
        return confirmed;
    }

    @Transactional
    public ObjectionDecision returnDecision(long objectionId, long decisionId, String reason, AdminActor actor) {
        Objection objection = lock(objectionId);
        objection.returnDecision();

        LocalDateTime now = LocalDateTime.now(clock);
        ObjectionDecision returned = pending(objectionId, decisionId).returnBy(actor.adminCustomerId(), now, reason);
        recordPort.updateDecision(returned);
        recordPort.updateObjection(objection);

        Map<String, Object> detail = new HashMap<>();
        detail.put("objectionId", objectionId);
        detail.put("decisionId", decisionId);
        detail.put("result", returned.result().name());
        detail.put("decidedBy", returned.decidedBy());
        auditLogService.record(
                actor.adminCustomerId(),
                null,
                AuditEventType.OBJECTION_DECISION_RETURN,
                actor.requestIp(),
                true,
                detail);
        return returned;
    }

    private Objection lock(long objectionId) {
        return recordPort
                .findObjectionForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
    }

    private ObjectionDecision pending(long objectionId, long decisionId) {
        return recordPort
                .findDecisionById(decisionId)
                .filter(decision -> decision.objectionId() == objectionId && decision.pendingApproval())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND));
    }

    private PaymentStopCase caseOf(Objection objection) {
        return recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));
    }

    private static Map<String, Object> auditDetail(
            ObjectionDecision decision, PaymentStopCase paymentStopCase, GateResult gate) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("objectionId", decision.objectionId());
        detail.put("decisionId", decision.decisionId());
        detail.put("caseId", paymentStopCase.getCaseId());
        detail.put("accountId", paymentStopCase.getAccountId());
        detail.put("result", decision.result().name());
        detail.put("status", decision.status().name());
        detail.put("gateBranch", gate.branch().name());
        detail.put("rulesVersion", gate.rulesVersion());
        return detail;
    }
}
