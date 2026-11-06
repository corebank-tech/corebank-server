package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStatus;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ObjectionQueryUseCase {

    List<Summary> list();

    /** 상세는 소명 원문이 보이므로 조회 사유를 받고 감사를 남긴다(PH-89). 게이트는 조회 시점 원장으로 다시 계산한다. */
    Detail detail(long objectionId, AdminActor actor, AccessReason accessReason);

    List<ObjectionNotice> notices(long objectionId);

    /** 지급정지 계좌의 게이트 창 기간 원장. 원장 타임라인 차트용이다. */
    List<LedgerEntry> ledger(long objectionId);

    /**
     * @param latestRunStatus 가장 최근 분석 실행 상태. 실행이 없으면 null
     * @param latestRunLastStepTitle 그 실행의 마지막 단계 제목. 단계가 없으면 null
     */
    record Summary(
            Objection objection,
            PaymentStopCase paymentStopCase,
            Flags flags,
            AgentRunStatus latestRunStatus,
            String latestRunLastStepTitle) {}

    record Detail(
            Objection objection,
            PaymentStopCase paymentStopCase,
            GateResult gate,
            Optional<ObjectionDecision> decision,
            Optional<ObjectionDecision> pendingDecision,
            Flags flags,
            List<ExternalCheck> externalChecks,
            List<SupplementRequest> supplementRequests,
            int documentCount) {}

    /** 기한 경보(D12, 설계 불변식 6)와 보완 기한 경과(D32). */
    record Flags(boolean deadlineAlert, boolean supplementOverdue) {}

    record LedgerEntry(
            String transactionNumber,
            String direction,
            boolean interbank,
            Long counterpartyAccountId,
            long amount,
            LocalDateTime occurredAt,
            boolean disputed,
            boolean afterDispute) {}
}
