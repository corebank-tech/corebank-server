package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AgentRunQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentRunQueryService implements AgentRunQueryUseCase {

    private final AgentRunPort agentRunPort;
    private final PaymentStopRecordPort recordPort;

    @Override
    public List<AgentRun> runs(long objectionId) {
        requireObjection(objectionId);
        return agentRunPort.findRunsByObjection(objectionId);
    }

    @Override
    public List<AgentRunStep> steps(long objectionId, long runId, int afterSeq) {
        AgentRun run = agentRunPort
                .findRun(runId)
                .filter(found -> found.objectionId() == objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.AGENT_RUN_NOT_FOUND));
        return agentRunPort.findStepsAfter(run.runId(), Math.max(afterSeq, 0));
    }

    @Override
    public Optional<ReviewDraft> latestDraft(long objectionId) {
        requireObjection(objectionId);
        return agentRunPort.findLatestDraft(objectionId);
    }

    private void requireObjection(long objectionId) {
        if (recordPort.findObjection(objectionId).isEmpty()) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND);
        }
    }
}
