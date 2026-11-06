package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.agent.AgentStepStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepType;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 워커가 부르는 내부 API(D15). 작업 가져가기는 워커 서비스 키로, 나머지는 그 이의제기 하나에만 유효한 작업 토큰으로 인증한다.
 * Agent 는 읽기와 단계 이벤트, 판정안 제출만 할 수 있다. 해제, 확정, 계좌 변경 권한은 없다.
 */
public interface AgentWorkerUseCase {

    /** 대기 중인 분석 하나를 가져간다. 킬스위치가 꺼졌거나 대기열이 비었으면 빈 값이다. */
    Optional<ClaimedJob> claim(String workerKey);

    Context context(String jobToken, long objectionId);

    List<LedgerItem> ledger(
            String jobToken, long objectionId, LocalDateTime from, LocalDateTime to, String counterpartyKey);

    void recordStep(String jobToken, long runId, StepCommand command);

    DraftResult submitDraft(String jobToken, long objectionId, DraftCommand command);

    /** nextSeq 는 이 시도가 다음에 보낼 단계 순번이다. 재시도면 앞선 시도의 단계 뒤에 이어 붙인다. */
    record ClaimedJob(long runId, long objectionId, int attempt, int nextSeq, String jobToken) {}

    /** 가명 처리한 소명과 접수 정보. 신분증 사본은 확인 여부만 준다(D37). */
    record Context(
            long objectionId,
            long runId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            LocalDate objectionDeadline,
            List<LedgerItem> disputedTransfers,
            List<String> commonDocuments,
            LocalDateTime ledgerFrom,
            LocalDateTime ledgerTo) {}

    /**
     * 원장 거래 하나. 상대는 가명 키로만 준다.
     *
     * @param counterpartyKey 당행 상대의 가명 키. 이 이의제기 안에서만 같은 값이다. 타행이면 null
     * @param afterDispute 첫 다툼 거래 이후 거래. 정상 근거로 쓰지 않는다
     */
    record LedgerItem(
            String transactionNumber,
            String direction,
            boolean interbank,
            String counterpartyKey,
            long amount,
            LocalDateTime occurredAt,
            boolean disputed,
            boolean afterDispute) {}

    record StepCommand(
            int seq,
            AgentStepType type,
            String title,
            String summary,
            Map<String, Object> detail,
            AgentStepStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {}

    record DraftCommand(
            GateBranch branch,
            List<String> evidenceTransactionNumbers,
            List<String> missingDocuments,
            List<Map<String, Object>> claims,
            List<Map<String, Object>> contradictions,
            List<String> followUps,
            String noticeDraft,
            String model,
            String promptVersion,
            Long inputTokens,
            Long outputTokens,
            Long costMicroUsd) {}

    /** 서버가 판정안 제출 뒤 다시 계산한 게이트 갈래. Agent 갈래와 다르면 화면이 경고한다. */
    record DraftResult(long runId, GateBranch agentBranch, GateBranch gateBranch) {}
}
