package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.application.port.out.DocumentStoragePort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.application.service.JobTokenService.JobClaims;
import com.shinhan.corebank.paymentstop.config.AgentProperties;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepType;
import com.shinhan.corebank.paymentstop.domain.agent.Pseudonymizer;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.OcrPage;
import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import com.shinhan.corebank.paymentstop.domain.gate.RuleResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 워커용 내부 API 의 업무 규칙(D15, D20). 워커는 읽기, 단계 이벤트, 판정안 제출만 한다. 판정안을 받으면 서버가 게이트를 다시
 * 계산해 GATE_EVALUATED 단계로 남긴다. 판정안은 해제 근거가 아니다(D9).
 */
@Service
@RequiredArgsConstructor
public class AgentJobService implements AgentWorkerUseCase {

    private static final int CLAIM_CANDIDATES = 5;
    private static final String PURPOSE_ANALYSIS = "ANALYSIS";
    private static final String LEASE_EXPIRED = "점유 시간 안에 단계를 남기지 않아 이전 시도를 중단함";
    private static final String LEASE_EXHAUSTED = "점유 만료가 반복돼 분석을 끝내지 못함";

    // 금감원 공통 소명자료 안내를 바탕으로 한 내부 목록이다. Agent 가 누락 서류를 짚을 때 기준으로 쓴다
    private static final List<String> COMMON_DOCUMENTS =
            List.of("거래 상대방과의 계약서나 주문 내역", "입금 대가를 제공했다는 증빙(배송, 용역 완료 등)", "거래 상대방과의 이전 거래 내역", "사업 대금이면 사업자등록증");

    private final AgentRunPort agentRunPort;
    private final PaymentStopRecordPort recordPort;
    private final LedgerFactPort ledgerFactPort;
    private final ObjectionReviewPort reviewPort;
    private final ObjectionDocumentPort documentPort;
    private final DocumentStoragePort storagePort;
    private final GateEvaluator gateEvaluator;
    private final JobTokenService jobTokenService;
    private final AgentProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public Optional<ClaimedJob> claim(String workerKey) {
        jobTokenService.verifyWorkerKey(workerKey);
        if (!properties.enabled()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (long closed : agentRunPort.failExhausted(now, properties.maxAttempts(), LEASE_EXHAUSTED)) {
            appendServerFailure(closed, LEASE_EXHAUSTED, now);
        }

        // 동시 요청은 같은 후보에 대한 조건부 UPDATE 에서 줄을 서고, 뒤에 온 쪽은 0행이라 다음 후보로 넘어간다
        for (long runId : agentRunPort.findClaimCandidates(now, properties.maxAttempts(), CLAIM_CANDIDATES)) {
            boolean expiredRetry = agentRunPort
                    .findRun(runId)
                    .map(before -> before.status() == AgentRunStatus.RUNNING)
                    .orElse(false);
            if (agentRunPort.tryClaim(runId, now, now.plus(properties.lease()), properties.maxAttempts())) {
                // 점유가 만료된 앞선 시도는 서버가 FAILED 로 남긴다(agent_run_step_schema §3). 이 UPDATE 가 실행 행을
                // 잠갔으므로 앞선 워커가 같은 순번을 동시에 넣을 수 없다
                if (expiredRetry) {
                    appendServerFailure(runId, LEASE_EXPIRED, now);
                }
                AgentRun run = agentRunPort.findRun(runId).orElseThrow();
                return Optional.of(new ClaimedJob(
                        runId,
                        run.objectionId(),
                        run.attempt(),
                        agentRunPort.maxSeq(runId) + 1,
                        jobTokenService.issue(runId, run.objectionId(), run.attempt(), now)));
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Context context(String jobToken, long objectionId) {
        AgentRun run = authorize(jobToken, objectionId, null, false);
        Target target = target(objectionId);
        GateEvaluator.Window window = gateEvaluator.window(target.paymentStopCase());

        List<LedgerItem> disputed = items(target, window, window.from(), window.to()).stream()
                .filter(LedgerItem::disputed)
                .toList();

        Objection objection = target.objection();
        List<ObjectionDocument> evidence = evidence(objectionId);
        Map<Long, DocumentOcr> ocrs = documentPort.findOcrs(
                evidence.stream().map(ObjectionDocument::documentId).toList());
        List<ContextDocument> documents = evidence.stream()
                .map(document -> {
                    DocumentOcr ocr = ocrs.get(document.documentId());
                    boolean done = ocr != null && ocr.status() == OcrStatus.DONE;
                    return new ContextDocument(
                            document.documentId(),
                            document.round(),
                            document.ocrStatus(),
                            done ? ocr.pages() : List.of());
                })
                .toList();
        List<SupplementRequest> supplements = reviewPort.findSupplementRequests(objectionId);
        return new Context(
                objectionId,
                run.runId(),
                objection.getClause(),
                Pseudonymizer.mask(objection.getStatement()),
                objection.isSimplifiedRequested(),
                objection.isIdCopyConfirmed(),
                objection.isLitigationPending(),
                target.paymentStopCase().getObjectionDeadline(),
                disputed,
                COMMON_DOCUMENTS,
                window.from(),
                window.to(),
                documents,
                workerChecks(objectionId),
                objection.getSupplementRound(),
                supplements.isEmpty() ? List.of() : supplements.getLast().missingDocuments());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerDocument> documents(String jobToken, long objectionId) {
        authorize(jobToken, objectionId, null, false);
        return evidence(objectionId).stream()
                .map(document -> new WorkerDocument(
                        document.documentId(), document.contentType(), document.round(), document.ocrStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerDocumentContent documentContent(String jobToken, long objectionId, long documentId) {
        authorize(jobToken, objectionId, null, false);
        ObjectionDocument document = evidenceDocument(objectionId, documentId);
        return new WorkerDocumentContent(
                document.contentType(), storagePort.read(document.storageKey(), document.encrypted()));
    }

    @Override
    @Transactional
    public List<OcrPage> saveOcr(String jobToken, long objectionId, long documentId, OcrCommand command) {
        authorize(jobToken, objectionId, null, false);
        ObjectionDocument document = evidenceDocument(objectionId, documentId);
        OcrStatus status = command.status() == null ? OcrStatus.DONE : command.status();
        if (status == OcrStatus.PENDING) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "OCR 결과 상태는 DONE 이나 FAILED 입니다.");
        }
        // 원문은 저장하지 않는다. 가명 처리한 값만 남기고 돌려준다
        DocumentOcr ocr = DocumentOcr.fromWorker(
                document.documentId(),
                status,
                status == OcrStatus.DONE ? command.pages() : List.of(),
                truncate(command.engine(), 40),
                LocalDateTime.now(clock));
        documentPort.saveOcr(ocr);
        return ocr.pages();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerExternalCheck> externalChecks(String jobToken, long objectionId) {
        authorize(jobToken, objectionId, null, false);
        return workerChecks(objectionId);
    }

    // 신분증 사본은 Agent 에게 목록, 내용, OCR 모두 보이지 않는다(D37)
    private List<ObjectionDocument> evidence(long objectionId) {
        return documentPort.findDocuments(objectionId).stream()
                .filter(document -> document.kind() == DocumentKind.EVIDENCE)
                .toList();
    }

    private ObjectionDocument evidenceDocument(long objectionId, long documentId) {
        return documentPort
                .findDocument(objectionId, documentId)
                .filter(document -> document.kind() == DocumentKind.EVIDENCE)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.DOCUMENT_NOT_FOUND));
    }

    private List<WorkerExternalCheck> workerChecks(long objectionId) {
        return reviewPort.findExternalChecks(objectionId).stream()
                .map(check -> new WorkerExternalCheck(check.agency(), check.status(), check.reply()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerItem> ledger(
            String jobToken, long objectionId, LocalDateTime from, LocalDateTime to, String counterpartyKey) {
        authorize(jobToken, objectionId, null, false);
        Target target = target(objectionId);
        GateEvaluator.Window window = gateEvaluator.window(target.paymentStopCase());

        // 게이트가 읽는 기간 밖은 주지 않는다. 판정안 근거도 이 기간 안에서만 받는다
        LocalDateTime clampedFrom = from == null || from.isBefore(window.from()) ? window.from() : from;
        LocalDateTime clampedTo = to == null || to.isAfter(window.to()) ? window.to() : to;
        if (!clampedFrom.isBefore(clampedTo)) {
            return List.of();
        }
        return items(target, window, clampedFrom, clampedTo).stream()
                .filter(item -> counterpartyKey == null || counterpartyKey.equals(item.counterpartyKey()))
                .toList();
    }

    @Override
    @Transactional
    public void recordStep(String jobToken, long runId, StepCommand command) {
        AgentRun run = authorize(jobToken, null, runId, true);
        if (command.type() == AgentStepType.GATE_EVALUATED) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "GATE_EVALUATED 단계는 서버만 남깁니다.");
        }

        String title = Pseudonymizer.mask(command.title());
        String summary = Pseudonymizer.mask(command.summary());
        Map<String, Object> detail = Pseudonymizer.maskMap(command.detail());

        // 같은 seq 재전송은 무시한다(네트워크 재시도). 내용이 다르면 다른 단계가 그 자리를 차지한 것이라 거절한다
        Optional<AgentRunStep> existing = agentRunPort.findStep(runId, command.seq());
        if (existing.isPresent()) {
            AgentRunStep saved = existing.get();
            boolean same = saved.type() == command.type()
                    && saved.title().equals(title)
                    && java.util.Objects.equals(saved.summary(), summary)
                    && java.util.Objects.equals(saved.detail(), detail);
            if (!same) {
                throw new BusinessException(PaymentStopErrorCode.STEP_CONFLICT);
            }
            return;
        }
        if (command.seq() != agentRunPort.maxSeq(runId) + 1) {
            throw new BusinessException(PaymentStopErrorCode.STEP_OUT_OF_ORDER);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        agentRunPort.insertStep(new AgentRunStep(
                runId,
                command.seq(),
                command.type(),
                title,
                summary,
                detail,
                command.status(),
                command.startedAt() == null ? now : command.startedAt(),
                command.endedAt()));

        if (command.type() == AgentStepType.FAILED) {
            String reason =
                    truncate(Pseudonymizer.mask(command.summary() == null ? command.title() : command.summary()));
            if (run.attempt() >= properties.maxAttempts()) {
                agentRunPort.fail(runId, now, reason);
            } else {
                agentRunPort.requeue(runId, reason);
            }
            return;
        }
        agentRunPort.extendLease(runId, now.plus(properties.lease()));
    }

    @Override
    @Transactional
    public DraftResult submitDraft(String jobToken, long objectionId, DraftCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobClaims claims = jobTokenService.verify(jobToken, now);
        if (claims.objectionId() != objectionId) {
            throw new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID);
        }
        AgentRun run = agentRunPort
                .findRunForUpdate(claims.runId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID));

        Target target = target(objectionId);

        // 같은 시도의 재제출은 처음 결과를 돌려준다(응답 유실 뒤 재시도)
        Optional<ReviewDraft> submitted = agentRunPort.findDraftByRun(run.runId());
        if (submitted.isPresent() && run.status() == AgentRunStatus.COMPLETED && run.attempt() == claims.attempt()) {
            GateResult gate = gateEvaluator.evaluate(target.paymentStopCase(), target.objection());
            return new DraftResult(run.runId(), submitted.get().branch(), gate.branch());
        }
        requireRunning(run, claims, now);
        if (command.branch() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        GateEvaluator.Window window = gateEvaluator.window(target.paymentStopCase());
        Set<String> allowed = items(target, window, window.from(), window.to()).stream()
                .map(LedgerItem::transactionNumber)
                .collect(Collectors.toSet());
        if (!allowed.containsAll(referencedTransactions(command))) {
            throw new BusinessException(PaymentStopErrorCode.EVIDENCE_OUT_OF_RANGE);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("evidenceTransactionNumbers", nullToEmpty(command.evidenceTransactionNumbers()));
        content.put("missingDocuments", nullToEmpty(command.missingDocuments()));
        content.put("claims", nullToEmpty(command.claims()));
        content.put("contradictions", nullToEmpty(command.contradictions()));
        content.put("followUps", nullToEmpty(command.followUps()));
        content.put("noticeDraft", command.noticeDraft());
        agentRunPort.saveDraft(
                new ReviewDraft(run.runId(), objectionId, command.branch(), Pseudonymizer.maskMap(content), now));

        GateResult gate = gateEvaluator.evaluate(target.paymentStopCase(), target.objection());
        recordPort.saveGateEvaluation(objectionId, PURPOSE_ANALYSIS, gate, gateEvaluator.settings(), now);
        agentRunPort.insertStep(new AgentRunStep(
                run.runId(),
                agentRunPort.maxSeq(run.runId()) + 1,
                AgentStepType.GATE_EVALUATED,
                "해제 요건 점검",
                gate.branch() == command.branch() ? "서버 규칙 결과가 판정안 갈래와 같음" : "서버 규칙 결과가 판정안 갈래와 다름",
                gateDetail(gate, command),
                AgentStepStatus.DONE,
                now,
                now));
        agentRunPort.complete(
                run.runId(),
                now,
                command.model(),
                command.promptVersion(),
                command.inputTokens(),
                command.outputTokens(),
                command.costMicroUsd());

        return new DraftResult(run.runId(), command.branch(), gate.branch());
    }

    private void appendServerFailure(long runId, String reason, LocalDateTime now) {
        agentRunPort.insertStep(new AgentRunStep(
                runId,
                agentRunPort.maxSeq(runId) + 1,
                AgentStepType.FAILED,
                "분석 시도 중단",
                reason,
                Map.of("by", "SERVER"),
                AgentStepStatus.FAILED,
                now,
                now));
    }

    // 토큰 서명과 만료, 대상 대조, 실행 상태와 시도 번호를 본다. 실패는 모두 같은 PST0102 다
    private AgentRun authorize(String jobToken, Long objectionId, Long runId, boolean lock) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobClaims claims = jobTokenService.verify(jobToken, now);
        if ((objectionId != null && claims.objectionId() != objectionId)
                || (runId != null && claims.runId() != runId)) {
            throw new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID);
        }
        AgentRun run = (lock ? agentRunPort.findRunForUpdate(claims.runId()) : agentRunPort.findRun(claims.runId()))
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID));
        requireRunning(run, claims, now);
        return run;
    }

    // 점유가 만료됐거나 다른 시도로 넘어간 실행은 앞선 토큰으로 건드리지 못한다
    private static void requireRunning(AgentRun run, JobClaims claims, LocalDateTime now) {
        if (run.status() != AgentRunStatus.RUNNING
                || run.attempt() != claims.attempt()
                || run.leaseUntil() == null
                || run.leaseUntil().isBefore(now)) {
            throw new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID);
        }
    }

    private Target target(long objectionId) {
        Objection objection = recordPort
                .findObjection(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        PaymentStopCase paymentStopCase = recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));
        return new Target(objection, paymentStopCase);
    }

    private List<LedgerItem> items(Target target, GateEvaluator.Window window, LocalDateTime from, LocalDateTime to) {
        Set<String> disputedNumbers = target.paymentStopCase().getDisputedTransfers().stream()
                .map(DisputedTransfer::transactionNumber)
                .collect(Collectors.toSet());
        long objectionId = target.objection().getObjectionId();
        return ledgerFactPort.findByAccount(target.paymentStopCase().getAccountId(), from, to).stream()
                .map(fact -> toItem(fact, objectionId, disputedNumbers, window))
                .toList();
    }

    private LedgerItem toItem(LedgerFact fact, long objectionId, Set<String> disputed, GateEvaluator.Window window) {
        return new LedgerItem(
                fact.transactionNumber(),
                fact.incoming() ? "IN" : "OUT",
                fact.interbank(),
                fact.counterpartyAccountId() == null
                        ? null
                        : jobTokenService.counterpartyKey(objectionId, fact.counterpartyAccountId()),
                fact.amount(),
                fact.occurredAt(),
                disputed.contains(fact.transactionNumber()),
                !fact.occurredAt().isBefore(window.firstDisputedAt()) && !disputed.contains(fact.transactionNumber()));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> referencedTransactions(DraftCommand command) {
        Set<String> referenced = new HashSet<>(nullToEmpty(command.evidenceTransactionNumbers()));
        for (Map<String, Object> contradiction : nullToEmpty(command.contradictions())) {
            Object numbers = contradiction.get("transactionNumbers");
            if (numbers instanceof List<?> list) {
                list.forEach(number -> referenced.add(String.valueOf(number)));
            }
        }
        return referenced;
    }

    private static Map<String, Object> gateDetail(GateResult gate, DraftCommand command) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("branch", gate.branch().name());
        detail.put("agentBranch", command.branch().name());
        detail.put("rulesVersion", gate.rulesVersion());
        Map<String, Object> allowed = new LinkedHashMap<>();
        for (DecisionResult result : DecisionResult.values()) {
            allowed.put(result.name(), gate.allowsRelease(result));
        }
        detail.put("allowedResults", allowed);
        List<Map<String, Object>> rules = new ArrayList<>();
        for (RuleResult rule : gate.rules()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rule", rule.rule().name());
            item.put("matched", rule.matched());
            item.put("transactionNumbers", rule.usedTransactionNumbers());
            item.put("detail", rule.detail());
            rules.add(item);
        }
        detail.put("rules", rules);
        return detail;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String truncate(String text) {
        return truncate(text, 200);
    }

    private static String truncate(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) : text;
    }

    private record Target(Objection objection, PaymentStopCase paymentStopCase) {}
}
