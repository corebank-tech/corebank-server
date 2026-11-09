package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObjectionQueryService implements ObjectionQueryUseCase {

    // 목록은 최근 순으로 이만큼만. 페이지와 검색 조건은 관리자 화면(PA-07)과 함께 정한다
    private static final int LIST_LIMIT = 200;

    private final PaymentStopRecordPort recordPort;
    private final ObjectionReviewPort reviewPort;
    private final ObjectionDocumentPort documentPort;
    private final AgentRunPort agentRunPort;
    private final LedgerFactPort ledgerFactPort;
    private final GateEvaluator gateEvaluator;
    private final ObjectionCalendar objectionCalendar;
    private final AuditLogService auditLogService;

    // 화면의 행마다 따로 부르지 않게(N+1) 사건, 최근 실행, 마지막 단계, 재량 판정 여부를 각각 한 번에 읽는다
    @Override
    @Transactional(readOnly = true)
    public List<Summary> list() {
        List<Objection> objections = recordPort.findRecentObjections(LIST_LIMIT);
        Set<Long> objectionIds =
                objections.stream().map(Objection::getObjectionId).collect(Collectors.toSet());
        Map<Long, PaymentStopCase> cases = recordPort.findCases(
                objections.stream().map(Objection::getCaseId).collect(Collectors.toSet()));
        Map<Long, AgentRun> latestRuns = agentRunPort.findLatestRuns(objectionIds);
        Map<Long, String> lastStepTitles = agentRunPort.findLastStepTitles(
                latestRuns.values().stream().map(AgentRun::runId).toList());
        Set<Long> discretionary = recordPort.findDiscretionaryObjectionIds(objectionIds);
        ObjectionCalendar.Today today = objectionCalendar.snapshot();

        return objections.stream()
                .map(objection -> {
                    AgentRun run = latestRuns.get(objection.getObjectionId());
                    return new Summary(
                            objection,
                            cases.get(objection.getCaseId()),
                            today.flags(objection),
                            run == null ? null : run.status(),
                            run == null ? null : lastStepTitles.get(run.runId()),
                            discretionary.contains(objection.getObjectionId()));
                })
                .toList();
    }

    // 조회 감사 행을 같은 트랜잭션에 쓰므로 읽기 전용으로 두지 않는다
    @Override
    @Transactional
    public Detail detail(long objectionId, AdminActor actor, AccessReason accessReason) {
        Objection objection = objection(objectionId);
        PaymentStopCase paymentStopCase = caseOf(objection);

        auditLogService.recordPersonalDataAccess(
                actor.adminCustomerId(),
                PersonalDataAccessEventType.OBJECTION_DETAIL_VIEW,
                actor.requestIp(),
                true,
                Map.of("objectionId", objectionId, "caseId", paymentStopCase.getCaseId()),
                accessReason);

        return new Detail(
                objection,
                paymentStopCase,
                gateEvaluator.evaluate(paymentStopCase, objection),
                recordPort.findConfirmedDecision(objectionId),
                recordPort.findPendingDecision(objectionId),
                objectionCalendar.snapshot().flags(objection),
                reviewPort.findExternalChecks(objectionId),
                reviewPort.findSupplementRequests(objectionId),
                documentPort.countDocuments(objectionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectionNotice> notices(long objectionId) {
        objection(objectionId);
        return reviewPort.findNotices(objectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(long objectionId) {
        PaymentStopCase paymentStopCase = caseOf(objection(objectionId));
        GateEvaluator.Window window = gateEvaluator.window(paymentStopCase);
        Set<String> disputed = paymentStopCase.getDisputedTransfers().stream()
                .map(DisputedTransfer::transactionNumber)
                .collect(Collectors.toSet());
        return ledgerFactPort.findByAccount(paymentStopCase.getAccountId(), window.from(), window.to()).stream()
                .map(fact -> new LedgerEntry(
                        fact.transactionNumber(),
                        fact.incoming() ? "IN" : "OUT",
                        fact.interbank(),
                        fact.counterpartyAccountId(),
                        fact.amount(),
                        fact.occurredAt(),
                        disputed.contains(fact.transactionNumber()),
                        !fact.occurredAt().isBefore(window.firstDisputedAt())
                                && !disputed.contains(fact.transactionNumber())))
                .toList();
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
