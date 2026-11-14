package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** TransferHeld를 조사 작업으로 내구화하고 별도 워커 시도를 조건부 전이한다. */
@Service
@RequiredArgsConstructor
public class FdsInvestigationJobService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> TERMINAL_HOLD_STATUSES =
            Set.of("EXECUTED", "REJECTED", "FAILED", "CANCELLED", "EXPIRED");
    private final FdsInvestigationJobPort jobs;
    private final FdsInvestigationInputService inputs;
    private final TransferHoldUseCase holds;
    private final Clock clock;

    public FdsInvestigationJob register(TransferHeld event) {
        LocalDateTime now = now();
        return jobs.createIfAbsent(event.transferHoldId(), UUID.randomUUID().toString(), now);
    }

    public int recoverExpired() {
        return jobs.recoverExpired(now());
    }

    public void runDue(FdsInvestigationAgentPort agent, int limit) {
        for (Long holdId : jobs.findDue(now(), limit)) {
            // 뒤쪽 작업도 실행 전에 온전한 임대 시간을 갖도록 선점 시각을 매번 계산한다.
            runOne(agent, holdId, now());
        }
    }

    void runOne(FdsInvestigationAgentPort agent, long holdId, LocalDateTime startedAt) {
        Integer attemptNo = jobs.claim(holdId, startedAt, startedAt.plusSeconds(70));
        if (attemptNo == null) return;
        FdsInvestigationJob job = jobs.findByHoldId(holdId);
        if (job == null || job.attemptCount() != attemptNo) return;
        try {
            TransferHoldForFds hold = holds.findForFds(holdId);
            if (closed(hold)) {
                jobs.fail(holdId, attemptNo, "HOLD_CLOSED", now());
                return;
            }
            // Python 콜백도 같은 실행 세대를 돌려주도록 선점 번호를 조사 입력에 고정한다.
            FdsInvestigationInput input =
                    inputs.build(holdId, job.investigationId()).withAttemptNo(attemptNo);
            FdsInvestigationAgentPort.Result result;
            try {
                result = agent.investigate(input);
            } catch (FdsInvestigationAgentPort.Failure exception) {
                if (exception.retryable()) finishTransientFailure(job, attemptNo, "AGENT_UNAVAILABLE");
                else jobs.fail(holdId, attemptNo, "AGENT_PERMANENT_FAILURE", now());
                return;
            } catch (RuntimeException exception) {
                finishTransientFailure(job, attemptNo, "AGENT_UNAVAILABLE");
                return;
            }
            if (result == null
                    || !job.investigationId().equals(result.investigationId())
                    || holdId != result.transferHoldId()
                    || !"COMPLETED".equals(result.status())) {
                jobs.fail(holdId, attemptNo, "INVALID_AGENT_ACK", now());
                return;
            }
            // Python 성공은 콜백이 보고서와 COMPLETED를 먼저 커밋한 경우에만 인정한다.
            FdsInvestigationJob completed = jobs.findByHoldId(holdId);
            if (completed == null
                    || !"COMPLETED".equals(completed.status())
                    || !jobs.hasReport(job.investigationId())) {
                finishTransientFailure(job, attemptNo, "AGENT_ACK_WITHOUT_REPORT");
            }
        } catch (RuntimeException exception) {
            finishTransientFailure(job, attemptNo, "INVESTIGATION_INFRA_FAILURE");
        }
    }

    private void finishTransientFailure(FdsInvestigationJob job, int attemptNo, String reason) {
        LocalDateTime now = now();
        if (job.attemptCount() >= 3) {
            jobs.fail(job.transferHoldId(), attemptNo, reason, now);
            return;
        }
        Duration delay = job.attemptCount() == 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);
        // attemptNo는 만료된 이전 실행이 새 실행 상태를 덮어쓰지 못하게 하는 펜싱 토큰이다.
        jobs.retry(job.transferHoldId(), attemptNo, now.plus(delay), reason, now);
    }

    private static boolean closed(TransferHoldForFds hold) {
        return hold == null || TERMINAL_HOLD_STATUSES.contains(hold.status());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST));
    }
}
